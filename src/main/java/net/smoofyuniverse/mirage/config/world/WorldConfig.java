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

package net.smoofyuniverse.mirage.config.world;

import com.google.common.collect.ImmutableList;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifiers;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.config.world.MainConfig.Resolved;
import net.smoofyuniverse.mirage.util.IOUtil;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.api.world.WorldTypes;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.spongepowered.math.GenericMath.clamp;

public class WorldConfig {
	public static final int CURRENT_VERSION = 3, MINIMUM_VERSION = 3;
	public static final WorldConfig DISABLED;

	public final Resolved main;
	public final List<ConfiguredModifier> modifiers;
	public final long seed;

	public WorldConfig(Resolved main, List<ConfiguredModifier> modifiers, long seed) {
		this.main = main;
		this.modifiers = ImmutableList.copyOf(modifiers);
		this.seed = seed;
	}

	public static WorldConfig load(Path file) throws IOException {
		ConfigurationLoader<CommentedConfigurationNode> loader = Mirage.get().createConfigLoader(file);

		CommentedConfigurationNode root = loader.load();
		int version = root.node("Version").getInt();
		if (version > CURRENT_VERSION || version < MINIMUM_VERSION) {
			version = CURRENT_VERSION;
			if (IOUtil.backup(file).isPresent()) {
				Mirage.LOGGER.info("Your config version is not supported. A new one will be generated.");
				root = loader.createNode();
			}
		}

		ConfigurationNode cfgNode = root.node("Config");
		MainConfig cfg = cfgNode.get(MainConfig.class);
		if (cfg == null)
			cfg = new MainConfig();

		Registry<ChunkModifier> modifierRegistry = ChunkModifier.REGISTRY_TYPE.get();
		Registry<WorldType> worldTypeRegistry = RegistryTypes.WORLD_TYPE.get();
		WorldType worldType = null;

		if (cfg.worldType == null) {
			// Guess using filename
			String fn = file.getFileName().toString();
			int i = fn.lastIndexOf('.');
			if (i != -1)
				fn = fn.substring(0, i);

			try {
				ResourceKey key = ResourceKey.resolve(fn);
				worldType = worldTypeRegistry.findValue(key).orElse(null);
				if (worldType != null)
					cfg.worldType = key;
			} catch (Exception ignored) {
			}
		} else {
			worldType = worldTypeRegistry.findValue(cfg.worldType).orElse(null);
		}

		cfg.deobf.naturalRadius = clamp(cfg.deobf.naturalRadius, 1, 4);
		cfg.deobf.playerRadius = clamp(cfg.deobf.playerRadius, 1, 4);

		ConfigurationNode modsNode = root.node("Modifiers");
		if (modsNode.virtual()) {
			if (worldType == WorldTypes.OVERWORLD.get()) {
				modsNode.appendListNode().node("Type").set(modifierRegistry.valueKey(ChunkModifiers.RANDOM_BEDROCK));

				ConfigurationNode water_dungeons = modsNode.appendListNode();
				water_dungeons.node("Type").set(modifierRegistry.valueKey(ChunkModifiers.HIDE_OBVIOUS));
				water_dungeons.node("Preset").set("water_dungeons");
			}

			modsNode.appendListNode().node("Type").set(modifierRegistry.valueKey(ChunkModifiers.HIDE_OBVIOUS));
		}

		ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();
		if (cfg.enabled) {
			for (ConfigurationNode node : modsNode.childrenList()) {
				String type = node.node("Type").getString();
				if (type == null)
					continue;

				ChunkModifier mod = modifierRegistry.findValue(ResourceKey.resolve(type)).orElse(null);
				if (mod == null) {
					Mirage.LOGGER.warn("Modifier '" + type + "' does not exists.");
					continue;
				}

				Object modCfg;
				try {
					String preset = node.node("Preset").getString();
					modCfg = mod.loadConfiguration(node.node("Options"), worldType, preset == null ? "" : preset.toLowerCase());
					node.removeChild("Preset");
				} catch (Exception e) {
					Mirage.LOGGER.warn("Modifier " + ChunkModifier.REGISTRY_TYPE.get().valueKey(mod) + " failed to loaded his configuration. This modifier will be ignored.", e);
					continue;
				}

				mods.add(new ConfiguredModifier(mod, modCfg));
			}
		}

		List<ConfiguredModifier> modifiers = mods.build();
		if (cfg.enabled && modifiers.isEmpty()) {
			Mirage.LOGGER.info("No valid modifier was found. Obfuscation will be disabled.");
			cfg.enabled = false;
		}

		if (cfg.enabled && cfg.cache) {
			boolean requireCache = false;
			for (ConfiguredModifier mod : modifiers) {
				if (mod.modifier.requireCache()) {
					requireCache = true;
					break;
				}
			}

			if (!requireCache) {
				Mirage.LOGGER.info("No modifier requiring cache was found. Cache will be disabled.");
				cfg.cache = false;
			}
		}

		root.node("Version").set(version);
		cfgNode.set(cfg);
		loader.save(root);

		return new WorldConfig(cfg.resolve(worldType), modifiers, 0);
	}

	static {
		MainConfig main = new MainConfig();
		main.enabled = false;
		DISABLED = new WorldConfig(main.resolve(null), ImmutableList.of(), 0);
	}
}
