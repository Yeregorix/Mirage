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
import com.thomas15v.noxray.modifier.GenerationModifier;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;

import java.io.IOException;

@Plugin(id = "noxray", name = "NoXray", version = "0.3.3-beta", authors = "thomas15v", description = "Anti-Xray")
public class NoXrayPlugin {

	@Inject
	private Game game;

	private static NoXrayPlugin instance;

	public static NoXrayPlugin getInstance() {
		return instance;
	}

	private BlockModifier blockModifier;

	@Inject
	private Logger logger;

	@Inject
	@DefaultConfig(sharedRoot = false)
	private ConfigurationLoader<CommentedConfigurationNode> loader;

	@Inject
	private GuiceObjectMapperFactory factory;

	private NoXrayConfig config;

	@Listener
	public void onStart(GameLoadCompleteEvent event) {
		loadConfig();
		blockModifier = new GenerationModifier();
		instance = this;
		game.getEventManager().registerListeners(this, new ChunkEventListener());
		game.getEventManager().registerListeners(this, new PlayerEventListener());
		if (config.isUseOreDict()) {
			try {
				OreUtil.registerForgeOres();
			} catch (NoClassDefFoundError ignored) {
			}
		}
	}

	@Listener
	public void onServerStarted(GameStartedServerEvent event) {
		this.logger.info("Loaded successfully.");
	}

	public BlockModifier getBlockModifier() {
		return blockModifier;
	}

	private void loadConfig() {

		try {
			CommentedConfigurationNode node = loader.load(ConfigurationOptions.defaults().setObjectMapperFactory(factory).setShouldCopyDefaults(true));
			config = node.getValue(TypeToken.of(NoXrayConfig.class), new NoXrayConfig());
			loader.save(node);
		} catch (IOException | ObjectMappingException e) {
			e.printStackTrace();
		}
	}

	public Logger getLogger() {
		return logger;
	}
}
