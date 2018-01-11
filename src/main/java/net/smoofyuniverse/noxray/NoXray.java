/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.noxray;

import com.google.inject.Inject;
import net.smoofyuniverse.noxray.event.WorldEventListener;
import net.smoofyuniverse.noxray.impl.internal.InternalChunk;
import net.smoofyuniverse.noxray.impl.network.NetworkChunk;
import net.smoofyuniverse.noxray.impl.network.NetworkChunk.State;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.World;

import java.nio.file.Path;

@Plugin(id = "noxray", name = "NoXray", version = "1.0.0", authors = {"thomas15v", "Yeregorix"}, description = "A simple but powerful Anti-Xray")
public class NoXray {
	public static final Logger LOGGER = LoggerFactory.getLogger("NoXray");
	private static NoXray instance;

	@Inject
	private Game game;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	private GuiceObjectMapperFactory factory;

	private ConfigurationOptions configOptions;
	private Task updateTask;

	public NoXray() {
		if (instance != null)
			throw new IllegalStateException();
		instance = this;
	}

	@Listener
	public void onLoadComplete(GamePreInitializationEvent e) {
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
						netChunk.sendBlockChanges();
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

	public static NoXray get() {
		if (instance == null)
			throw new IllegalStateException("Instance not available");
		return instance;
	}
}
