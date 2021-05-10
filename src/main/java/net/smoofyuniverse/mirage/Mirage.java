/*
 * Copyright (c) 2018-2021 Hugo Dupanloup (Yeregorix)
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
import net.smoofyuniverse.map.WorldMap;
import net.smoofyuniverse.map.WorldMapConfig;
import net.smoofyuniverse.map.WorldMapLoader;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.config.serializer.BlockSetSerializer;
import net.smoofyuniverse.mirage.config.world.WorldConfig;
import net.smoofyuniverse.mirage.event.WorldEventListener;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import net.smoofyuniverse.mirage.impl.internal.InternalServer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.resource.Pack;
import net.smoofyuniverse.mirage.resource.Resources;
import net.smoofyuniverse.mirage.util.IOUtil;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import net.smoofyuniverse.mirage.util.collection.BlockSet.SerializationPredicate;
import net.smoofyuniverse.ore.update.UpdateChecker;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(id = "mirage", name = "Mirage", version = "1.4.0", authors = "Yeregorix", description = "The best solution against xray users")
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

	private Path cacheDir;
	private WorldMapLoader<WorldConfig> configMapLoader;
	private WorldMap<WorldConfig> configMap;

	private Task obfuscationTask;

	public Mirage() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onGamePreInit(GamePreInitializationEvent e) {
		this.game.getRegistry().registerModule(ChunkModifier.class, ChunkModifierRegistryModule.get());
		TypeSerializerCollection.defaults().register(BlockSet.TOKEN, new BlockSetSerializer(SerializationPredicate.limit(0.6f)));

		this.cacheDir = this.game.getGameDirectory().resolve("mirage-cache");

		try {
			Files.createDirectories(this.configDir);
		} catch (IOException ignored) {
		}

		this.configMapLoader = new WorldMapLoader<WorldConfig>(LOGGER,
				IOUtil.createConfigLoader(this.configDir.resolve("map.conf")),
				this.configDir.resolve("configs"), WorldConfig.DISABLED) {
			@Override
			protected void initMap(WorldMapConfig map) {
				for (DimensionType dim : new DimensionType[]{DimensionTypes.NETHER, DimensionTypes.THE_END})
					map.dimensions.putIfAbsent(dim, getShortId(dim));
			}

			@Override
			protected WorldConfig loadConfig(Path file) throws Exception {
				return WorldConfig.load(file);
			}
		};
	}

	@Listener
	public void onGameInit(GameInitializationEvent e) {
		LOGGER.info("Optimizing exposition check performances ..");
		for (BlockState b : this.game.getRegistry().getAllOf(BlockState.class)) {
			try {
				((InternalBlockState) b).optimizeExpositionCheck();
			} catch (Exception ex) {
				LOGGER.warn("Failed to optimize block: " + b.getId(), ex);
			}
		}

		try {
			Resources.loadResources(Pack.loadAll());
		} catch (Exception ex) {
			LOGGER.warn("Failed to load resources", ex);
		}

		loadConfigs();

		if (this.game.getServer() instanceof InternalServer)
			this.game.getEventManager().registerListeners(this, new WorldEventListener());

		this.game.getEventManager().registerListeners(this, new UpdateChecker(LOGGER, this.container,
				IOUtil.createConfigLoader(this.configDir.resolve("update.conf")), "Yeregorix", "Mirage"));
	}

	private void loadConfigs() {
		if (Files.exists(this.configDir.resolve("worlds")) && Files.notExists(this.configDir.resolve("map.conf"))) {
			LOGGER.info("Updating config directory structure ...");
			Path worlds = IOUtil.backup(this.configDir).orElse(this.configDir).resolve("worlds");
			this.configMap = this.configMapLoader.importWorlds(worlds);
		} else {
			this.configMap = this.configMapLoader.load();
		}
	}

	@Listener
	public void onGameReload(GameReloadEvent e) {
		loadConfigs();
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent e) {
		if (this.game.getServer() instanceof InternalServer) {
			this.obfuscationTask = Task.builder().execute(() -> {
				for (World w : this.game.getServer().getWorlds()) {
					for (NetworkChunk chunk : ((InternalWorld) w).getView().getLoadedChunkViews()) {
						if (chunk.getState() == State.OBFUSCATION_REQUESTED)
							chunk.obfuscate();
					}
				}
			}).intervalTicks(1).submit(this);

			LOGGER.info("Mirage " + this.container.getVersion().orElse("?") + " was loaded successfully.");
		} else {
			LOGGER.error("!!WARNING!! Mirage was not loaded correctly. Be sure that the jar file is at the root of your mods folder!");
		}
	}

	@Listener
	public void onServerStopping(GameStoppingServerEvent e) {
		if (this.obfuscationTask != null) {
			this.obfuscationTask.cancel();
			this.obfuscationTask = null;
		}
	}

	public Path getConfigDirectory() {
		return this.configDir;
	}

	public Path getCacheDirectory() {
		return this.cacheDir;
	}

	public WorldConfig getConfig(World world) {
		return this.configMap.get(world.getProperties());
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
