/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import net.smoofyuniverse.map.WorldMap;
import net.smoofyuniverse.map.WorldMapConfig;
import net.smoofyuniverse.map.WorldMapLoader;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifiers;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.config.resources.Resources;
import net.smoofyuniverse.mirage.config.resources.ResourcesLoader;
import net.smoofyuniverse.mirage.config.world.WorldConfig;
import net.smoofyuniverse.mirage.event.BlockListener;
import net.smoofyuniverse.mirage.event.ChunkListener;
import net.smoofyuniverse.mirage.impl.internal.InternalServer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.ore.update.UpdateChecker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Server;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.*;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Ticks;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.plugin.PluginContainer;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Mirage {
	public static final Logger LOGGER = LogManager.getLogger("Mirage");
	private static Mirage instance;

	@Inject
	private Game game;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	private PluginContainer container;

	private Path cacheDir;

	private ConfigurationOptions configOptions;
	private WorldMapLoader<WorldConfig> configMapLoader;
	private WorldMap<WorldConfig> configMap;

	private Map<ResourceKey, Resources> worldTypeToResources;

	private ScheduledTask obfuscationTask;

	public Mirage() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onConstructPlugin(ConstructPluginEvent e) {
		this.cacheDir = this.game.gameDirectory().resolve("mirage-cache");
		this.configOptions = ConfigurationOptions.defaults().implicitInitialization(false).serializers(this.game.configManager().serializers());

		try {
			Files.createDirectories(this.configDir);
		} catch (IOException ignored) {
		}

		this.configMapLoader = new WorldMapLoader<WorldConfig>(LOGGER,
				createConfigLoader(this.configDir.resolve("map.conf")),
				this.configDir.resolve("configs"), WorldConfig.DISABLED) {
			@Override
			protected void initMap(WorldMapConfig map) {
				for (String name : new String[]{"the_nether", "the_end"})
					map.types.put(ResourceKey.minecraft(name), name);
			}

			@Override
			protected WorldConfig loadConfig(Path file) throws Exception {
				return WorldConfig.load(file);
			}
		};
	}

	public ConfigurationLoader<CommentedConfigurationNode> createConfigLoader(Path file) {
		return HoconConfigurationLoader.builder().defaultOptions(this.configOptions).path(file).build();
	}

	public ConfigurationLoader<CommentedConfigurationNode> createConfigLoader(URL url) {
		return HoconConfigurationLoader.builder().defaultOptions(this.configOptions).url(url).build();
	}

	@Listener
	public void onRegisterRegistry(RegisterRegistryEvent.GameScoped e) {
		e.register(ChunkModifier.REGISTRY_TYPE_KEY, true, () -> ImmutableMap.of(
				key("hide_all"), ChunkModifiers.HIDE_ALL,
				key("hide_obvious"), ChunkModifiers.HIDE_OBVIOUS,
				key("random_bedrock"), ChunkModifiers.RANDOM_BEDROCK,
				key("random_block"), ChunkModifiers.RANDOM_BLOCK
		));
	}

	public static ResourceKey key(String value) {
		return ResourceKey.of("mirage", value);
	}

	@Listener
	public void onServerStarting(StartingEngineEvent<Server> e) {
		loadConfigs();

		EventManager em = this.game.eventManager();
		if (e.engine() instanceof InternalServer) {
			em.registerListeners(this.container, new BlockListener());
			em.registerListeners(this.container, new ChunkListener());
		}

		em.registerListeners(this.container, new UpdateChecker(LOGGER, this.container,
				createConfigLoader(this.configDir.resolve("update.conf")), "Yeregorix", "Mirage"));
	}

	private void loadConfigs() {
		ResourcesLoader loader = new ResourcesLoader(this);
		loader.addDefault();
		loader.addDirectory(this.configDir.resolve("packs"));
		this.worldTypeToResources = loader.build();

		this.configMap = this.configMapLoader.load();
	}

	@Listener
	public void onRefreshGame(RefreshGameEvent e) {
		loadConfigs();
	}

	@Listener
	public void onServerStarted(StartedEngineEvent<Server> e) {
		Server server = e.engine();
		if (server instanceof InternalServer) {
			this.obfuscationTask = server.scheduler().submit(Task.builder().execute(() -> {
				for (ServerWorld w : server.worldManager().worlds()) {
					((InternalWorld) w).view().loadedOpaqueChunks()
							.filter(chunk -> chunk.state() == State.OBFUSCATION_REQUESTED)
							.forEach(NetworkChunk::obfuscate);
				}
			}).interval(Ticks.of(1)).plugin(this.container).build());

			LOGGER.info("Mirage " + this.container.metadata().version() + " was loaded successfully.");
		} else {
			LOGGER.error("!!WARNING!! Mirage was not loaded correctly. Be sure that the jar file is at the root of your mods folder!");
		}
	}

	public Path getConfigDirectory() {
		return this.configDir;
	}

	public Path getCacheDirectory() {
		return this.cacheDir;
	}

	@Listener
	public void onServerStopping(StoppingEngineEvent<Server> e) {
		if (this.obfuscationTask != null) {
			this.obfuscationTask.cancel();
			this.obfuscationTask = null;
		}
	}

	public PluginContainer getContainer() {
		return this.container;
	}

	public static Mirage get() {
		if (instance == null)
			throw new IllegalStateException("Instance not available");
		return instance;
	}

	public WorldConfig getConfig(ServerWorld world) {
		return this.configMap.get(world.properties());
	}

	public Resources getResources(ResourceKey worldType) {
		if (worldType == null)
			throw new IllegalArgumentException("worldType");

		Resources r = this.worldTypeToResources.get(worldType);
		if (r == null) {
			Mirage.LOGGER.warn("No resources are registered for world type " + worldType + ".");
			r = new Resources(worldType);
			this.worldTypeToResources.put(worldType, r);
		}
		return r;
	}
}
