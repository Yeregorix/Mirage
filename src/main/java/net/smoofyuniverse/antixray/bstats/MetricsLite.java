package net.smoofyuniverse.antixray.bstats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * bStats collects some data for plugin authors.
 * <p>
 * Check out https://bStats.org/ to learn more about bStats!
 */
public class MetricsLite {

	// The version of this bStats class
	public static final int B_STATS_VERSION = 1;
	// The url to which the data is sent
	private static final String URL = "https://bStats.org/submitData/sponge";
	// A list with all known metrics class objects including this one
	private static final List<Object> knownMetricsInstances = new ArrayList<>();
	// We use this flag to ensure only one instance of this class exist
	private static boolean created = false;
	// The plugin
	private final PluginContainer plugin;
	// The logger
	private Logger logger;
	// Is bStats enabled on this server?
	private boolean enabled;

	// The uuid of the server
	private String serverUUID;

	// Should failed requests be logged?
	private boolean logFailedRequests = false;
	// The config path
	private Path configDir;

	// The constructor is not meant to be called by the user himself.
	// The instance is created using Dependency Injection (https://docs.spongepowered.org/master/en/plugin/injection.html)
	@Inject
	private MetricsLite(PluginContainer plugin, Logger logger, @ConfigDir(sharedRoot = true) Path configDir) {
		if (created)
			throw new IllegalStateException("There's already an instance of this Metrics class!");
		created = true;

		this.plugin = plugin;
		this.logger = logger;
		this.configDir = configDir;

		try {
			loadConfig();
		} catch (IOException e) {
			// Failed to load configuration
			this.logger.warn("Failed to load bStats config!", e);
			return;
		}

		// We are not allowed to send data about this server :(
		if (!this.enabled)
			return;

		Class<?> usedMetricsClass = getFirstBStatsClass();
		if (usedMetricsClass == null) {
			// Failed to get first metrics class
			return;
		}

		if (usedMetricsClass == getClass()) {
			// We are the first! :)
			linkMetrics(this);
			startSubmitting();
		} else {
			// We aren't the first so we link to the first metrics class
			try {
				usedMetricsClass.getMethod("linkMetrics", Object.class).invoke(null, this);
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				if (this.logFailedRequests)
					this.logger.warn("Failed to link to first metrics class {}!", usedMetricsClass.getName(), e);
			}
		}
	}

	/**
	 * Loads the bStats configuration.
	 *
	 * @throws IOException If something did not work :(
	 */
	private void loadConfig() throws IOException {
		Path configPath = this.configDir.resolve("bStats"), configFile = configPath.resolve("config.conf");
		Files.createDirectories(configPath);
		HoconConfigurationLoader configLoader = HoconConfigurationLoader.builder().setPath(configFile).build();

		CommentedConfigurationNode node;
		if (Files.exists(configFile)) {
			node = configLoader.load();
		} else {
			node = configLoader.createEmptyNode();

			// Add default values
			node.getNode("enabled").setValue(true);
			// Every server gets it's unique random id.
			node.getNode("serverUuid").setValue(UUID.randomUUID().toString());
			// Should failed request be logged?
			node.getNode("logFailedRequests").setValue(false);

			// Add information about bStats
			node.getNode("enabled").setComment(
					"bStats collects some data for plugin authors like how many servers are using their plugins.\n"
							+ "To honor their work, you should not disable it.\n"
							+ "This has nearly no effect on the server performance!\n"
							+ "Check out https://bStats.org/ to learn more :)"
			);

			configLoader.save(node);
		}

		// Load configuration
		this.enabled = node.getNode("enabled").getBoolean(true);
		this.serverUUID = node.getNode("serverUuid").getString();
		this.logFailedRequests = node.getNode("logFailedRequests").getBoolean();
	}

	/**
	 * Gets the first bStat Metrics class.
	 *
	 * @return The first bStats metrics class.
	 */
	private Class<?> getFirstBStatsClass() {
		Path configPath = this.configDir.resolve("bStats"), tempFile = configPath.resolve("temp.txt");

		try {
			Files.createDirectories(configPath);

			if (Files.exists(tempFile)) {
				Optional<String> className = Files.lines(tempFile).findFirst();
				if (className.isPresent()) {
					try {
						// Let's check if a class with the given name exists.
						return Class.forName(className.get());
					} catch (ClassNotFoundException ignored) {
					}
				}
			}

			Files.write(tempFile, Arrays.asList(getClass().getName(), "Note: This class only exists for internal purpose. You can ignore it :)"));

			return getClass();
		} catch (IOException e) {
			if (this.logFailedRequests)
				this.logger.warn("Failed to get first bStats class!", e);
			return null;
		}
	}

	/**
	 * Links an other metrics class with this class.
	 * This method is called using Reflection.
	 *
	 * @param metrics An object of the metrics class to link.
	 */
	public static void linkMetrics(Object metrics) {
		knownMetricsInstances.add(metrics);
	}

	private void startSubmitting() {
		// Submit the data every 30 minutes, first time after 5 minutes to give other plugins enough time to start
		// WARNING: Changing the frequency has no effect but your plugin WILL be blocked/deleted!

		Task.builder().execute(t -> {
			if (Sponge.getPluginManager().isLoaded(this.plugin.getId())) {
				// The data collection (e.g. for custom graphs) is done sync
				// Don't be afraid! The connection to the bStats server is still async, only the stats collection is sync ;)

				submitData();
			} else {
				t.cancel();
			}
		}).delay(5, TimeUnit.MINUTES).interval(30, TimeUnit.MINUTES).submit(this.plugin);
	}

	/**
	 * Collects the data and sends it afterwards.
	 */
	private void submitData() {
		JsonObject data = getServerData();

		JsonArray pluginData = new JsonArray();
		// Search for all other bStats Metrics classes to get their plugin data
		for (Object metrics : knownMetricsInstances) {
			try {
				Object plugin = metrics.getClass().getMethod("getPluginData").invoke(metrics);
				if (plugin instanceof JsonObject)
					pluginData.add((JsonObject) plugin);
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
			}
		}

		data.add("plugins", pluginData);

		// Create a new thread for the connection to the bStats server
		Task.builder().async().execute(() -> {
			try {
				// Send the data
				sendData(data);
			} catch (Exception e) {
				// Something went wrong! :(
				if (this.logFailedRequests)
					this.logger.warn("Could not submit plugin stats!", e);
			}
		}).submit(this.plugin);
	}

	/**
	 * Gets the server specific data.
	 *
	 * @return The server specific data.
	 */
	private JsonObject getServerData() {
		Game game = Sponge.getGame();
		JsonObject data = new JsonObject();

		data.addProperty("serverUUID", serverUUID);

		// Minecraft specific data
		int players = game.getServer().getOnlinePlayers().size();
		data.addProperty("playerAmount", players > 200 ? 200 : players);
		data.addProperty("onlineMode", game.getServer().getOnlineMode() ? 1 : 0);
		data.addProperty("minecraftVersion", game.getPlatform().getMinecraftVersion().getName());
		data.addProperty("spongeImplementation", game.getPlatform().getContainer(Platform.Component.IMPLEMENTATION).getName());

		// OS/Java specific data
		data.addProperty("javaVersion", System.getProperty("java.version"));
		data.addProperty("osName", System.getProperty("os.name"));
		data.addProperty("osArch", System.getProperty("os.arch"));
		data.addProperty("osVersion", System.getProperty("os.version"));
		data.addProperty("coreCount", Runtime.getRuntime().availableProcessors());

		return data;
	}

	/**
	 * Sends the data to the bStats server.
	 *
	 * @param data The data to send.
	 * @throws Exception If the request failed.
	 */
	private static void sendData(JsonObject data) throws Exception {
		Validate.notNull(data, "Data cannot be null");
		HttpsURLConnection co = (HttpsURLConnection) new URL(URL).openConnection();

		// Compress the data to save bandwidth
		byte[] bytes = compress(data.toString());

		// Add headers
		co.setRequestMethod("POST");
		co.addRequestProperty("Accept", "application/json");
		co.addRequestProperty("Connection", "close");
		co.addRequestProperty("Content-Encoding", "gzip"); // We gzip our request
		co.addRequestProperty("Content-Length", String.valueOf(bytes.length));
		co.setRequestProperty("Content-Type", "application/json"); // We send our data in JSON format
		co.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

		// Send data
		co.setDoOutput(true);
		DataOutputStream out = new DataOutputStream(co.getOutputStream());
		out.write(bytes);
		out.flush();
		out.close();

		co.getInputStream().close(); // We don't care about the response - Just send our data :)
	}

	/**
	 * Gzips the given String.
	 *
	 * @param str The string to gzip.
	 * @return The gzipped String.
	 * @throws IOException If the compression failed.
	 */
	private static byte[] compress(String str) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(str.getBytes("UTF-8"));
		gzip.close();
		return out.toByteArray();
	}

	/**
	 * Gets the plugin specific data.
	 * This method is called using Reflection.
	 *
	 * @return The plugin specific data.
	 */
	public JsonObject getPluginData() {
		JsonObject data = new JsonObject();

		data.addProperty("pluginName", this.plugin.getName());
		data.addProperty("pluginVersion", this.plugin.getVersion().orElse("unknown"));
		data.add("customCharts", new JsonArray());

		return data;
	}
}