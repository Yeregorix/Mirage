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

package com.thomas15v.noxray;

import com.google.inject.Inject;
import com.thomas15v.noxray.event.WorldEventListener;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
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
import org.spongepowered.api.world.World;

import java.nio.file.Path;

@Plugin(id = "noxray", name = "NoXray", version = "0.4.0-beta", authors = "thomas15v", description = "Anti-Xray")
public class NoXrayPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("NoXray");
	private static NoXrayPlugin instance;

	@Inject
	private Game game;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	private GuiceObjectMapperFactory factory;

	private ConfigurationOptions configOptions;
	private Task updateTask;

	@Listener
	public void onLoadComplete(GamePreInitializationEvent e) {
		instance = this;
		this.configOptions = ConfigurationOptions.defaults().setObjectMapperFactory(this.factory).setShouldCopyDefaults(true);
		this.game.getEventManager().registerListeners(this, new WorldEventListener());
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent e) {
		this.updateTask = Task.builder().execute(() -> {
			for (World w : this.game.getServer().getWorlds())
				((InternalWorld) w).getNetworkWorld().sendBlockChanges();
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

	public static NoXrayPlugin get() {
		return instance;
	}
}
