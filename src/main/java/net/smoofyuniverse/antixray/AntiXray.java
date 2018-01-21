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
import net.smoofyuniverse.antixray.event.WorldEventListener;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk.State;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(id = "antixray", name = "AntiXray", version = "1.0.0", authors = "Yeregorix", description = "A simple but powerful Anti-Xray")
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

	private ConfigurationOptions configOptions;
	private Task updateTask;
	private Path cacheDir;

	public AntiXray() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onGamePreInit(GamePreInitializationEvent e) {
		this.cacheDir = Sponge.getGame().getGameDirectory().resolve("antixray-cache");
		try {
			Files.createDirectory(this.configDir);
		} catch (IOException ignored) {
		}
		this.configOptions = ConfigurationOptions.defaults().setObjectMapperFactory(this.factory).setShouldCopyDefaults(true);
		this.game.getEventManager().registerListeners(this, new WorldEventListener());
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent e) {
		this.updateTask = Task.builder().execute(() -> {
			for (World w : this.game.getServer().getWorlds()) {
				for (Chunk c : w.getLoadedChunks()) {
					NetworkChunk netChunk = ((InternalChunk) c).getView();
					if (netChunk != null) {
						if (netChunk.getState() == State.NEED_REOBFUSCATION)
							netChunk.obfuscate();
						netChunk.getListener().sendChanges();
					}
				}
			}
		}).intervalTicks(1).submit(this);

		LOGGER.info("Loaded successfully.");
	}

	@Listener
	public void onServerStopping(GameStoppingServerEvent e) {
		this.updateTask.cancel();
	}

	public ConfigurationLoader<CommentedConfigurationNode> createConfigLoader(String worldName) {
		return HoconConfigurationLoader.builder().setPath(this.configDir.resolve(worldName + ".conf")).setDefaultOptions(this.configOptions).build();
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
