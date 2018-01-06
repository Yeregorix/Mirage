/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.thomas15v.noxray.api.BlockModifier;
import com.thomas15v.noxray.config.NoXrayConfig;
import com.thomas15v.noxray.event.ChunkEventListener;
import com.thomas15v.noxray.event.PlayerEventListener;
import com.thomas15v.noxray.modifications.OreUtil;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;

import java.io.IOException;

@Plugin(id = "noxray", name = "NoXray", version = "0.4.0-beta", authors = "thomas15v", description = "Anti-Xray")
public class NoXrayPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("NoXray");
	private static NoXrayPlugin instance;

	@Inject
	private Game game;
	@Inject
	@DefaultConfig(sharedRoot = false)
	private ConfigurationLoader<CommentedConfigurationNode> loader;
	@Inject
	private GuiceObjectMapperFactory factory;

	private NoXrayConfig config;
	private BlockModifier blockModifier;
	private float density;

	@Listener
	public void onLoadComplete(GamePreInitializationEvent e) {
		instance = this;
		loadConfig();
		this.blockModifier = this.config.getModifier();
		this.density = this.config.getDensity();
		if (this.config.useOreDict()) {
			try {
				OreUtil.registerForgeOres();
			} catch (NoClassDefFoundError ignored) {
			}
		}

		this.game.getEventManager().registerListeners(this, new ChunkEventListener());
		this.game.getEventManager().registerListeners(this, new PlayerEventListener());
	}

	private void loadConfig() {
		try {
			CommentedConfigurationNode node = this.loader.load(ConfigurationOptions.defaults().setObjectMapperFactory(this.factory).setShouldCopyDefaults(true));
			this.config = node.getValue(TypeToken.of(NoXrayConfig.class), new NoXrayConfig());
			this.loader.save(node);
		} catch (IOException | ObjectMappingException e) {
			LOGGER.warn("Error occured while loading config file", e);
		}
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent event) {
		LOGGER.info("Loaded successfully.");
	}

	public BlockModifier getBlockModifier() {
		return this.blockModifier;
	}

	public float getDensity() {
		return this.density;
	}

	public static NoXrayPlugin get() {
		return instance;
	}
}
