/*
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

package net.smoofyuniverse.antixray.modifier;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap.Entry;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.api.cache.Signature.Builder;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifier;
import net.smoofyuniverse.antixray.api.volume.BlockView;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import net.smoofyuniverse.antixray.util.ModifierUtil;
import net.smoofyuniverse.antixray.util.collection.BlockSet;
import net.smoofyuniverse.antixray.util.collection.WeightedList;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.*;

import static net.smoofyuniverse.antixray.util.MathUtil.clamp;

/**
 * This modifier only hides ores which are not exposed and generates thousands of fake ores to hides things such as caves, bases and remaining ores
 */
public class RandomModifier extends ChunkModifier {

	public RandomModifier() {
		super(AntiXray.get(), "Random");
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldProperties world, String preset) throws ObjectMappingException {
		Config cfg = node.getValue(Config.TOKEN, new Config());

		DimensionType dimType = world.getDimensionType();
		if (cfg.blocks == null) {
			cfg.blocks = new BlockSet();
			ModifierUtil.getCommonResources(cfg.blocks, dimType);
			ModifierUtil.getRareResources(cfg.blocks, dimType);
			cfg.blocks.add(ModifierUtil.getCommonGround(dimType).getType());
		}

		if (cfg.replacements == null) {
			cfg.replacements = new HashMap<>();

			BlockSet set = new BlockSet();
			ModifierUtil.getCommonResources(set, dimType);

			for (BlockType type : set.getInternalTypes())
				cfg.replacements.put(type.getDefaultState(), 1d);

			for (Entry<BlockState> e : set.getInternalStates().object2BooleanEntrySet()) {
				if (e.getBooleanValue())
					cfg.replacements.put(e.getKey(), 1d);
				else
					cfg.replacements.remove(e.getKey());
			}

			cfg.replacements.put(ModifierUtil.getCommonGround(dimType), (double) cfg.replacements.size());
		}

		cfg.dynamism = clamp(cfg.dynamism, 0, 10);

		node.setValue(Config.TOKEN, cfg);
		return cfg.toImmutable();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		builder.append(cfg.blocks).append(cfg.replacements);
	}

	@Override
	public boolean isReady(ChunkView view, Object config) {
		return view.areNeighborsLoaded();
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		boolean useDynamism = cfg.dynamism != 0 && view.isDynamismEnabled();

		for (int y = min.getY(); y <= max.getY(); y++) {
			for (int z = min.getZ(); z <= max.getZ(); z++) {
				for (int x = min.getX(); x <= max.getX(); x++) {
					BlockState b = view.getBlock(x, y, z);
					if (b.getType() == BlockTypes.AIR)
						continue;

					if (cfg.blocks.contains(b)) {
						if (view.isExposed(x, y, z)) {
							if (useDynamism) {
								view.setDynamism(x, y, z, cfg.dynamism);
								view.setBlock(x, y, z, cfg.replacements.get(r));
							}
						} else {
							view.setBlock(x, y, z, cfg.replacements.get(r));
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
		@Setting(value = "Replacements", comment = "Blocks and their weight used to randomly replace hidden blocks")
		public Map<BlockState, Double> replacements;
		@Setting(value = "Dynamism", comment = "The dynamic obfuscation distance, between 0 and 10")
		public int dynamism = 4;

		public Immutable toImmutable() {
			return new Immutable(this.blocks.toSet(), WeightedList.of(this.replacements), this.dynamism);
		}

		public static final class Immutable {
			public final Set<BlockState> blocks;
			public final WeightedList<BlockState> replacements;
			public final int dynamism;

			public Immutable(Collection<BlockState> blocks, WeightedList<BlockState> replacements, int dynamism) {
				this.blocks = ImmutableSet.copyOf(blocks);
				this.replacements = replacements;
				this.dynamism = dynamism;
			}
		}
	}
}
