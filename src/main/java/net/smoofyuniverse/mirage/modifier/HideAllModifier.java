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

package net.smoofyuniverse.mirage.modifier;

import com.google.common.collect.ImmutableSet;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.config.pack.Resources;
import net.smoofyuniverse.mirage.modifier.HideAllModifier.Config.Resolved;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.math.vector.Vector3i;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.smoofyuniverse.mirage.config.pack.Resources.COMMON;
import static net.smoofyuniverse.mirage.config.pack.Resources.RARE;
import static net.smoofyuniverse.mirage.util.BlockUtil.AIR;
import static net.smoofyuniverse.mirage.util.MathUtil.clampY;
import static net.smoofyuniverse.mirage.util.RegistryUtil.resolveBlockState;
import static net.smoofyuniverse.mirage.util.RegistryUtil.resolveBlockStates;

/**
 * This modifier hides all ores.
 */
public class HideAllModifier implements ChunkModifier {

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldType worldType, ConfigurationNode preset) throws SerializationException {
		Config cfg = node.get(Config.class);
		if (cfg == null)
			cfg = new Config();

		if (preset != null) {
			if (cfg.blocks == null)
				cfg.blocks = Resources.getBlocks(preset, COMMON, RARE);
			if (cfg.replacement == null)
				cfg.replacement = Resources.getGround(preset).orElse(null);
			if (cfg.minY == null)
				cfg.minY = preset.node("MinY").get(Integer.class);
			if (cfg.maxY == null)
				cfg.maxY = preset.node("MaxY").get(Integer.class);
		}

		if (cfg.blocks == null)
			cfg.blocks = new HashSet<>();
		if (cfg.replacement == null)
			cfg.replacement = "minecraft:stone";
		if (cfg.minY == null)
			cfg.minY = Integer.MIN_VALUE;
		if (cfg.maxY == null)
			cfg.maxY = Integer.MAX_VALUE;

		cfg.minY = clampY(worldType, cfg.minY);
		cfg.maxY = clampY(worldType, cfg.maxY);

		if (cfg.minY > cfg.maxY) {
			int t = cfg.minY;
			cfg.minY = cfg.maxY;
			cfg.maxY = t;
		}

		node.set(cfg);
		return cfg.resolve();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Resolved cfg = (Resolved) config;
		builder.append(cfg.blocks).append(cfg.replacement).append(cfg.minY).append(cfg.maxY);
	}

	@Override
	public boolean requireNeighborsLoaded() {
		return false;
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Resolved cfg = (Resolved) config;
		final int maxX = max.x(), maxY = Math.min(max.y(), cfg.maxY), maxZ = max.z();

		for (int y = Math.max(min.y(), cfg.minY); y <= maxY; y++) {
			for (int z = min.z(); z <= maxZ; z++) {
				for (int x = min.x(); x <= maxX; x++) {
					BlockState b = view.block(x, y, z);
					if (b == AIR || b == cfg.replacement)
						continue;

					if (cfg.blocks.contains(b))
						view.setBlock(x, y, z, cfg.replacement);
				}
			}
		}
	}

	@org.spongepowered.configurate.objectmapping.ConfigSerializable
	public static final class Config {
		@Comment("Blocks that will be hidden by the modifier")
		@Setting("Blocks")
		public Set<String> blocks;

		@Comment("The block used to replace hidden blocks")
		@Setting("Replacement")
		public String replacement;

		@Comment("The minimum Y of the section to obfuscate")
		@Setting("MinY")
		public Integer minY;

		@Comment("The maximum Y of the section to obfuscate")
		@Setting("MaxY")
		public Integer maxY;

		public Resolved resolve() {
			return new Resolved(resolveBlockStates(this.blocks),
					resolveBlockState(this.replacement), this.minY, this.maxY);
		}

		public static final class Resolved {
			public final Set<BlockState> blocks;
			public final BlockState replacement;
			public final int minY, maxY;

			public Resolved(Collection<BlockState> blocks, BlockState replacement, int minY, int maxY) {
				this.blocks = ImmutableSet.copyOf(blocks);
				this.replacement = replacement;
				this.minY = minY;
				this.maxY = maxY;
			}
		}
	}
}
