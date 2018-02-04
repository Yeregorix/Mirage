/*
 * The MIT License (MIT)
 *
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

package net.smoofyuniverse.antixray;

import com.google.inject.Inject;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifier;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.antixray.bstats.Metrics;
import net.smoofyuniverse.antixray.config.serializer.BlockSetSerializer;
import net.smoofyuniverse.antixray.event.WorldEventListener;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk.State;
import net.smoofyuniverse.antixray.util.collection.BlockSet;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameConstructionEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(id = "antixray", name = "AntiXray", version = "1.2.2", authors = "Yeregorix", description = "A powerful solution against xray users")
public class AntiXray {
	public static final Logger LOGGER = LoggerFactory.getLogger("AntiXray");
	private static AntiXray instance;

	@Inject
	private Game game;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	private GuiceObjectMapperFactory factory;
	@Inject
	private PluginContainer container;

	@Inject
	private Metrics metrics;

	private ConfigurationOptions configOptions;
	private Task updateTask;
	private Path cacheDir, worldConfigsDir;

	public AntiXray() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onGameConstruction(GameConstructionEvent e) {
		this.game.getRegistry().registerModule(ChunkModifier.class, ChunkModifierRegistryModule.get());
		TypeSerializers.getDefaultSerializers().registerType(BlockSet.TOKEN, new BlockSetSerializer());
	}

	@Listener
	public void onGamePreInit(GamePreInitializationEvent e) {
		this.cacheDir = this.game.getGameDirectory().resolve("antixray-cache");
		this.worldConfigsDir = this.configDir.resolve("worlds");
		try {
			Files.createDirectories(this.worldConfigsDir);
		} catch (IOException ignored) {
		}
		this.configOptions = ConfigurationOptions.defaults().setObjectMapperFactory(this.factory).setShouldCopyDefaults(true);
		this.game.getEventManager().registerListeners(this, new WorldEventListener());
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent e) {
		if (this.game.getServer().getWorlds().iterator().next() instanceof InternalWorld) {
			this.updateTask = Task.builder().execute(() -> {
				for (World w : this.game.getServer().getWorlds()) {
					for (NetworkChunk chunk : ((InternalWorld) w).getView().getLoadedChunkViews()) {
						if (chunk.getState() == State.NEED_REOBFUSCATION)
							chunk.obfuscate();
						chunk.getListener().sendChanges();
					}
				}
			}).intervalTicks(1).submit(this);

			LOGGER.info("AntiXray " + this.container.getVersion().orElse("?") + " was loaded successfully.");
		} else {
			LOGGER.error("!!WARNING!! AntiXray was not loaded correctly. Be sure that the jar file is at the root of your mods folder!");
		}
	}

	@Listener
	public void onServerStopping(GameStoppingServerEvent e) {
		if (this.updateTask != null)
			this.updateTask.cancel();
	}

	public ConfigurationLoader<CommentedConfigurationNode> createConfigLoader(Path file) {
		return HoconConfigurationLoader.builder().setPath(file).setDefaultOptions(this.configOptions).build();
	}

	public boolean backupFile(Path file) throws IOException {
		if (!Files.exists(file))
			return false;

		String fn = file.getFileName() + ".backup";
		Path backup = null;
		for (int i = 0; i < 100; i++) {
			backup = file.resolveSibling(fn + i);
			if (!Files.exists(backup))
				break;
		}
		Files.move(file, backup);
		return true;
	}

	public Path getWorldConfigsDirectory() {
		return this.worldConfigsDir;
	}

	public Path getCacheDirectory() {
		return this.cacheDir;
	}

	public static AntiXray get() {
		if (instance == null)
			throw new IllegalStateException("Instance not available");
		return instance;
	}
}
