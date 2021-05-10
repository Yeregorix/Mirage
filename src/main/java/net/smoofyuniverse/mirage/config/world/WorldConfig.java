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

package net.smoofyuniverse.mirage.config.world;

import com.google.common.collect.ImmutableList;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifiers;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.util.IOUtil;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static net.smoofyuniverse.mirage.util.MathUtil.clamp;

public class WorldConfig {
	public static final int CURRENT_VERSION = 3, MINIMUM_VERSION = 1;
	public static final WorldConfig DISABLED;

	public final MainConfig.Immutable main;
	public final List<ConfiguredModifier> modifiers;
	public final long seed;

	public WorldConfig(MainConfig.Immutable main, List<ConfiguredModifier> modifiers, long seed) {
		this.main = main;
		this.modifiers = ImmutableList.copyOf(modifiers);
		this.seed = seed;
	}

	public static WorldConfig load(Path file) throws IOException, ObjectMappingException {
		ConfigurationLoader<CommentedConfigurationNode> loader = IOUtil.createConfigLoader(file);

		CommentedConfigurationNode root = loader.load();
		int version = root.getNode("Version").getInt();
		if (version > CURRENT_VERSION || version < MINIMUM_VERSION) {
			version = CURRENT_VERSION;
			if (IOUtil.backup(file).isPresent()) {
				Mirage.LOGGER.info("Your config version is not supported. A new one will be generated.");
				root = loader.createEmptyNode();
			}
		}

		ConfigurationNode cfgNode = root.getNode("Config");
		MainConfig cfg = cfgNode.getValue(MainConfig.TOKEN);
		if (cfg == null)
			cfg = new MainConfig();

		if (cfg.dimension == null) {
			// Guess
			String fn = file.getFileName().toString();
			int i = fn.lastIndexOf('.');
			cfg.dimension = Sponge.getRegistry().getType(DimensionType.class, i == -1 ? fn : fn.substring(0, i)).orElse(DimensionTypes.OVERWORLD);
		}

		cfg.deobf.naturalRadius = clamp(cfg.deobf.naturalRadius, 1, 4);
		cfg.deobf.playerRadius = clamp(cfg.deobf.playerRadius, 1, 4);

		ConfigurationNode modsNode = root.getNode("Modifiers");

		if (version == 1) {
			// Conversion "Id" -> "Type"
			for (ConfigurationNode node : modsNode.getChildrenList()) {
				ConfigurationNode typeNode = node.getNode("Type");
				if (typeNode.isVirtual()) {
					ConfigurationNode idNode = node.getNode("Id");
					if (!idNode.isVirtual()) {
						typeNode.setValue(idNode.getValue());
						node.removeChild("Id");
					}
				}
			}

			version = 2;
		}

		if (version == 2) {
			// No longer used
			cfgNode.removeChild("Preobfuscation");
			cfgNode.removeChild("Seed");

			// Update modifier types
			for (ConfigurationNode node : modsNode.getChildrenList()) {
				ConfigurationNode typeNode = node.getNode("Type");
				String type = typeNode.getString();
				if (type == null)
					continue;
				type = modifier2to3(type);
				if (type == null)
					node.removeChild("Type");
				else
					typeNode.setValue(type);
			}
			version = 3;
		}

		DimensionType dimType = cfg.dimension;

		if (modsNode.isVirtual()) {
			if (dimType == DimensionTypes.OVERWORLD) {
				modsNode.appendListNode().getNode("Type").setValue(ChunkModifiers.RANDOM_BEDROCK.getName());

				ConfigurationNode water_dungeons = modsNode.appendListNode();
				water_dungeons.getNode("Type").setValue(ChunkModifiers.HIDE_OBVIOUS.getName());
				water_dungeons.getNode("Preset").setValue("water_dungeons");
			}

			modsNode.appendListNode().getNode("Type").setValue(ChunkModifiers.HIDE_OBVIOUS.getName());
		}

		ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();
		if (cfg.enabled) {
			for (ConfigurationNode node : modsNode.getChildrenList()) {
				String type = node.getNode("Type").getString();
				if (type == null)
					continue;

				ChunkModifier mod = ChunkModifierRegistryModule.get().getById(type).orElse(null);
				if (mod == null) {
					Mirage.LOGGER.warn("Modifier '" + type + "' does not exists.");
					continue;
				}

				Object modCfg;
				try {
					String preset = node.getNode("Preset").getString();
					modCfg = mod.loadConfiguration(node.getNode("Options"), dimType, preset == null ? "" : preset.toLowerCase());
					node.removeChild("Preset");
				} catch (Exception e) {
					Mirage.LOGGER.warn("Modifier " + mod.getId() + " failed to loaded his configuration. This modifier will be ignored.", e);
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

		root.getNode("Version").setValue(version);
		cfgNode.setValue(MainConfig.TOKEN, cfg);
		loader.save(root);

		return new WorldConfig(cfg.toImmutable(), modifiers, 0);
	}

	private static String modifier2to3(String value) {
		switch (value.toLowerCase(Locale.ROOT)) {
			case "empty":
				return null;
			case "obvious":
				return "hide_obvious";
			case "bedrock":
				return "random_bedrock";
			case "random":
				return "random_block";
			case "fakegen":
				return "random_vein";
			default:
				return value;
		}
	}

	static {
		MainConfig main = new MainConfig();
		main.enabled = false;
		DISABLED = new WorldConfig(main.toImmutable(), ImmutableList.of(), 0);
	}
}
