/*
 * Copyright (c) 2018-2024 Hugo Dupanloup (Yeregorix)
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
import com.google.common.hash.Hashing;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.config.pack.Resources;
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
	public final long obfuscationSeed, fakeSeed, hashedFakeSeed;

	public WorldConfig(Resolved main, List<ConfiguredModifier> modifiers, long obfuscationSeed, long fakeSeed) {
		this.main = main;
		this.modifiers = ImmutableList.copyOf(modifiers);
		this.obfuscationSeed = obfuscationSeed;
		this.fakeSeed = fakeSeed;
		this.hashedFakeSeed = Hashing.sha256().hashLong(fakeSeed).asLong();
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

		Resources resources = Mirage.get().getResources();

		ConfigurationNode modsNode = root.node("Modifiers");
		if (modsNode.virtual()) {
			List<ConfigurationNode> defaultModifiers = resources.defaultModifiers.get(cfg.worldType);
			if (defaultModifiers.isEmpty()) {
				defaultModifiers = resources.defaultModifiers.get(WorldTypes.OVERWORLD.location());
			}

			modsNode.setList(ConfigurationNode.class, defaultModifiers);
		}

		boolean enabled = cfg.enabled;
		boolean cache = cfg.cache;

		ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();
		if (enabled) {
			for (ConfigurationNode node : modsNode.childrenList()) {
				String type = node.node("Type").getString();
				if (type == null)
					continue;

				ChunkModifier mod = modifierRegistry.findValue(Mirage.key(type)).orElse(null);
				if (mod == null) {
					Mirage.LOGGER.warn("Modifier '{}' does not exists.", type);
					continue;
				}

				Object modCfg;
				try {
					String preset = node.node("Preset").getString();
					ConfigurationNode presetNode = null;

					if (preset != null) {
						presetNode = resources.presets.get(preset.toLowerCase());
						if (presetNode == null) {
							Mirage.LOGGER.warn("Preset '{}' does not exists.", preset);
						} else {
							presetNode = presetNode.copy();
						}
					}

					modCfg = mod.loadConfiguration(node.node("Options"), worldType, presetNode);
				} catch (Exception e) {
					Mirage.LOGGER.warn("Modifier {} failed to loaded his configuration. This modifier will be ignored.", ChunkModifier.REGISTRY_TYPE.get().valueKey(mod), e);
					continue;
				}

				mods.add(new ConfiguredModifier(mod, modCfg));
			}
		}

		List<ConfiguredModifier> modifiers = mods.build();
		if (enabled && modifiers.isEmpty()) {
			Mirage.LOGGER.info("No valid modifier was found. Obfuscation will be disabled.");
			enabled = false;
		}

		if (enabled && cache) {
			boolean requireCache = false;
			for (ConfiguredModifier mod : modifiers) {
				if (mod.modifier.requireCache()) {
					requireCache = true;
					break;
				}
			}

			if (!requireCache) {
				Mirage.LOGGER.info("No modifier requiring cache was found. Cache will be disabled.");
				cache = false;
			}
		}

		root.node("Version").set(version);
		cfgNode.set(cfg);
		loader.save(root);

		cfg.enabled = enabled;
		cfg.cache = cache;
		return new WorldConfig(cfg.resolve(worldType), modifiers, 0, 0);
	}

	static {
		MainConfig main = new MainConfig();
		main.enabled = false;
		DISABLED = new WorldConfig(main.resolve(null), ImmutableList.of(), 0, 0);
	}
}
