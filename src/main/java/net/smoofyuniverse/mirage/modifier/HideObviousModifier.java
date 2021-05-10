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

package net.smoofyuniverse.mirage.modifier;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.resource.Resources;
import net.smoofyuniverse.mirage.util.BlockSet;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;

import java.util.Collection;
import java.util.Random;
import java.util.Set;

import static net.smoofyuniverse.mirage.resource.Categories.COMMON;
import static net.smoofyuniverse.mirage.resource.Categories.RARE;
import static net.smoofyuniverse.mirage.util.MathUtil.clamp;

/**
 * This modifier only hides ores which are not exposed to the view of normal users.
 */
public class HideObviousModifier extends ChunkModifier {

	public HideObviousModifier() {
		super(Mirage.get(), "hide_obvious");
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, DimensionType dimension, String preset) throws ObjectMappingException {
		Config cfg = node.getValue(Config.TOKEN);
		if (cfg == null)
			cfg = new Config();

		if (preset.equals("water_dungeons")) {
			cfg.blocks = new BlockSet();
			cfg.blocks.add(BlockTypes.SEA_LANTERN);
			cfg.blocks.add(BlockTypes.PRISMARINE);
			cfg.blocks.add(BlockTypes.GOLD_BLOCK);

			cfg.replacement = BlockTypes.WATER.getDefaultState();

			cfg.dynamism = 8;
			cfg.minY = 30;
			cfg.maxY = 64;
		} else {
			if (cfg.blocks == null)
				cfg.blocks = Resources.of(dimension).getBlocks(COMMON, RARE);
			if (cfg.replacement == null)
				cfg.replacement = Resources.of(dimension).getGround();

			cfg.dynamism = clamp(cfg.dynamism, 0, 10);
			cfg.minY = clamp(cfg.minY, 0, 255);
			cfg.maxY = clamp(cfg.maxY, 0, 255);

			if (cfg.minY > cfg.maxY) {
				int t = cfg.minY;
				cfg.minY = cfg.maxY;
				cfg.maxY = t;
			}
		}

		node.setValue(Config.TOKEN, cfg);
		return cfg.toImmutable();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		builder.append(cfg.blocks).append(cfg.replacement).append(cfg.dynamism).append(cfg.minY).append(cfg.maxY);
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		boolean useDynamism = cfg.dynamism != 0 && view.isDynamismEnabled();
		final int maxX = max.getX(), maxY = Math.min(max.getY(), cfg.maxY), maxZ = max.getZ();

		for (int y = Math.max(min.getY(), cfg.minY); y <= maxY; y++) {
			for (int z = min.getZ(); z <= maxZ; z++) {
				for (int x = min.getX(); x <= maxX; x++) {
					BlockState b = view.getBlock(x, y, z);
					if (b.getType() == BlockTypes.AIR || b == cfg.replacement)
						continue;

					if (cfg.blocks.contains(b)) {
						if (view.isExposed(x, y, z)) {
							if (useDynamism) {
								view.setDynamism(x, y, z, cfg.dynamism);
								view.setBlock(x, y, z, cfg.replacement);
							}
						} else {
							view.setBlock(x, y, z, cfg.replacement);
						}
					}
				}
			}
		}
	}

	@ConfigSerializable
	public static final class Config {
		public static final TypeToken<Config> TOKEN = TypeToken.of(Config.class);

		@Setting(value = "Blocks", comment = "Blocks that will be hidden by the modifier")
		public BlockSet blocks;
		@Setting(value = "Replacement", comment = "The block used to replace hidden blocks")
		public BlockState replacement;
		@Setting(value = "Dynamism", comment = "The dynamic obfuscation distance, between 0 and 10")
		public int dynamism = 4;
		@Setting(value = "MinY", comment = "The minimum Y of the section to obfuscate")
		public int minY = 0;
		@Setting(value = "MaxY", comment = "The maximum Y of the section to obfuscate")
		public int maxY = 255;

		public Immutable toImmutable() {
			return new Immutable(this.blocks.getAll(), this.replacement, this.dynamism, this.minY, this.maxY);
		}

		public static final class Immutable {
			public final Set<BlockState> blocks;
			public final BlockState replacement;
			public final int dynamism;
			public final int minY, maxY;

			public Immutable(Collection<BlockState> blocks, BlockState replacement, int dynamism, int minY, int maxY) {
				this.blocks = ImmutableSet.copyOf(blocks);
				this.replacement = replacement;
				this.dynamism = dynamism;
				this.minY = minY;
				this.maxY = maxY;
			}
		}
	}
}
