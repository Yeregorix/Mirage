/*
 * Copyright (c) 2018 Hugo Dupanloup (Yeregorix)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.smoofyuniverse.mirage;

import com.google.inject.Inject;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.config.global.GlobalConfig;
import net.smoofyuniverse.mirage.config.serializer.BlockSetSerializer;
import net.smoofyuniverse.mirage.event.PlayerEventListener;
import net.smoofyuniverse.mirage.event.WorldEventListener;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import net.smoofyuniverse.mirage.impl.internal.InternalServer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.ore.OreAPI;
import net.smoofyuniverse.mirage.resource.Resources;
import net.smoofyuniverse.mirage.util.IOUtil;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import net.smoofyuniverse.mirage.util.collection.BlockSet.SerializationPredicate;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.bstats.sponge.MetricsLite2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.*;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static net.smoofyuniverse.mirage.util.MathUtil.clamp;

@Plugin(id = "mirage", name = "Mirage", version = "1.3.4", authors = "Yeregorix", description = "The best solution against xray users")
public class Mirage {
	public static final Logger LOGGER = LoggerFactory.getLogger("Mirage");
	private static Mirage instance;

	@Inject
	private Game game;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	private PluginContainer container;
	@Inject
	private GuiceObjectMapperFactory factory;
	@Inject
	private MetricsLite2 metrics;

	private ConfigurationOptions configOptions;
	private Task updateTask;
	private Path cacheDir, worldConfigsDir, resourcesDir;

	private GlobalConfig.Immutable globalConfig;
	private Text[] updateMessages = new Text[0];

	public Mirage() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onGameConstruction(GameConstructionEvent e) {
		this.game.getRegistry().registerModule(ChunkModifier.class, ChunkModifierRegistryModule.get());
		TypeSerializers.getDefaultSerializers().registerType(BlockSet.TOKEN, new BlockSetSerializer(SerializationPredicate.limit(0.6f)));
	}

	@Listener
	public void onGamePreInit(GamePreInitializationEvent e) {
		this.cacheDir = this.game.getGameDirectory().resolve("mirage-cache");
		this.worldConfigsDir = this.configDir.resolve("worlds");
		this.resourcesDir = this.configDir.resolve("resources");

		try {
			Files.createDirectories(this.worldConfigsDir);
		} catch (IOException ignored) {
		}

		try {
			Files.createDirectories(this.resourcesDir);
		} catch (IOException ignored) {
		}

		this.configOptions = ConfigurationOptions.defaults().setObjectMapperFactory(this.factory);
	}

	@Listener
	public void onGameInit(GameInitializationEvent e) {
		LOGGER.info("Loading global configuration ..");
		try {
			loadGlobalConfig();
		} catch (Exception ex) {
			LOGGER.error("Failed to load global configuration", ex);
		}

		LOGGER.info("Optimizing exposition check performances ..");
		for (BlockState b : this.game.getRegistry().getAllOf(BlockState.class)) {
			try {
				((InternalBlockState) b).optimizeExpositionCheck();
			} catch (Exception ex) {
				LOGGER.warn("Failed to optimize block: " + b.getId(), ex);
			}
		}

		try {
			Resources.loadResources();
		} catch (Exception ex) {
			LOGGER.warn("Failed to load resources", ex);
		}

		if (this.game.getServer() instanceof InternalServer)
			this.game.getEventManager().registerListeners(this, new WorldEventListener());
		this.game.getEventManager().registerListeners(this, new PlayerEventListener());
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent e) {
		if (this.game.getServer() instanceof InternalServer) {
			this.updateTask = Task.builder().execute(() -> {
				for (World w : this.game.getServer().getWorlds()) {
					for (NetworkChunk chunk : ((InternalWorld) w).getView().getLoadedChunkViews()) {
						if (chunk.getState() == State.PREOBFUSCATED)
							chunk.obfuscate();
					}
				}
			}).intervalTicks(1).submit(this);

			LOGGER.info("Mirage " + this.container.getVersion().orElse("?") + " was loaded successfully.");
		} else {
			LOGGER.error("!!WARNING!! Mirage was not loaded correctly. Be sure that the jar file is at the root of your mods folder!");
		}

		if (this.globalConfig.updateCheck.enabled)
			Task.builder().async().interval(this.globalConfig.updateCheck.repetitionInterval, TimeUnit.HOURS).execute(this::checkForUpdate).submit(this);
	}

	@Listener
	public void onServerStopping(GameStoppingServerEvent e) {
		if (this.updateTask != null) {
			this.updateTask.cancel();
			this.updateTask = null;
		}
	}

	public void loadGlobalConfig() throws IOException, ObjectMappingException {
		if (this.globalConfig != null)
			throw new IllegalStateException("Config already loaded");

		Path file = this.configDir.resolve("global.conf");
		ConfigurationLoader<CommentedConfigurationNode> loader = createConfigLoader(file);

		CommentedConfigurationNode root = loader.load();
		int version = root.getNode("Version").getInt();
		if (version > GlobalConfig.CURRENT_VERSION || version < GlobalConfig.MINIMUM_VERSION) {
			version = GlobalConfig.CURRENT_VERSION;
			if (IOUtil.backupFile(file)) {
				LOGGER.info("Your global config version is not supported. A new one will be generated.");
				root = loader.createEmptyNode();
			}
		}

		ConfigurationNode cfgNode = root.getNode("Config");
		GlobalConfig cfg = cfgNode.getValue(GlobalConfig.TOKEN);
		if (cfg == null)
			cfg = new GlobalConfig();

		cfg.updateCheck.repetitionInterval = max(cfg.updateCheck.repetitionInterval, 0);
		cfg.updateCheck.consoleDelay = clamp(cfg.updateCheck.consoleDelay, -1, 100);
		cfg.updateCheck.playerDelay = clamp(cfg.updateCheck.playerDelay, -1, 100);

		if (cfg.updateCheck.consoleDelay == -1 && cfg.updateCheck.playerDelay == -1)
			cfg.updateCheck.enabled = false;

		root.getNode("Version").setValue(version);
		cfgNode.setValue(GlobalConfig.TOKEN, cfg);
		loader.save(root);

		this.globalConfig = cfg.toImmutable();
	}

	public void checkForUpdate() {
		String version = this.container.getVersion().orElse(null);
		if (version == null)
			return;

		LOGGER.debug("Checking for update ..");

		String latestVersion = null;
		try {
			latestVersion = OreAPI.getLatestVersion(OreAPI.getProjectVersions("mirage"), "7.1.0").orElse(null);
		} catch (Exception e) {
			LOGGER.info("Failed to check for update", e);
		}

		if (latestVersion != null && !latestVersion.equals(version)) {
			String downloadUrl = "https://ore.spongepowered.org/Yeregorix/Mirage/versions/" + latestVersion;

			Text msg1 = Text.join(Text.of("A new version of Mirage is available: "),
					Text.builder(latestVersion).color(TextColors.AQUA).build(),
					Text.of(". You're currently using version: "),
					Text.builder(version).color(TextColors.AQUA).build(),
					Text.of("."));

			Text msg2;
			try {
				msg2 = Text.builder("Click here to open the download page.").color(TextColors.GOLD)
						.onClick(TextActions.openUrl(new URL(downloadUrl))).build();
			} catch (MalformedURLException e) {
				msg2 = null;
			}

			if (this.globalConfig.updateCheck.consoleDelay != -1) {
				Task.builder().delayTicks(this.globalConfig.updateCheck.consoleDelay)
						.execute(() -> Sponge.getServer().getConsole().sendMessage(msg1)).submit(this);
			}

			if (this.globalConfig.updateCheck.playerDelay != -1)
				this.updateMessages = msg2 == null ? new Text[]{msg1} : new Text[]{msg1, msg2};
		}
	}

	public ConfigurationLoader<CommentedConfigurationNode> createConfigLoader(Path file) {
		return HoconConfigurationLoader.builder().setPath(file).setDefaultOptions(this.configOptions).build();
	}

	public Path getResourcesDirectory() {
		return this.resourcesDir;
	}

	public Path getWorldConfigsDirectory() {
		return this.worldConfigsDir;
	}

	public Path getCacheDirectory() {
		return this.cacheDir;
	}

	public GlobalConfig.Immutable getGlobalConfig() {
		if (this.globalConfig == null)
			throw new IllegalStateException("Config not loaded");
		return this.globalConfig;
	}

	public Text[] getUpdateMessages() {
		return this.updateMessages;
	}

	public PluginContainer getContainer() {
		return this.container;
	}

	public static Mirage get() {
		if (instance == null)
			throw new IllegalStateException("Instance not available");
		return instance;
	}
}
