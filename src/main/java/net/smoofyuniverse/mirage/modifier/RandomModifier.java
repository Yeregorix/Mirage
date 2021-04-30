/*
 * Copyright (c) 2018-2020 Hugo Dupanloup (Yeregorix)
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
import net.smoofyuniverse.bingo.WeightedList;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.resource.Resources;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.*;

import static net.smoofyuniverse.mirage.resource.Categories.*;
import static net.smoofyuniverse.mirage.util.MathUtil.clamp;

/**
 * This modifier only hides ores which are not exposed and generates thousands of fake ores to hides things such as caves, bases and remaining ores
 */
public class RandomModifier extends ChunkModifier {

	public RandomModifier() {
		super(Mirage.get(), "Random");
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldProperties world, String preset) throws ObjectMappingException {
		Config cfg = node.getValue(Config.TOKEN);
		if (cfg == null)
			cfg = new Config();

		if (cfg.blocks == null)
			cfg.blocks = Resources.of(world).getBlocks(GROUND, COMMON, RARE);

		if (cfg.replacements == null) {
			cfg.replacements = new HashMap<>();

			BlockSet set = Resources.of(world).getBlocks(COMMON);

			for (BlockState state : set.getAll())
				cfg.replacements.put(state, 1d);

			cfg.replacements.put(Resources.of(world).getGround(), Math.max(cfg.replacements.size(), 1d));
		}

		cfg.minY = clamp(cfg.minY, 0, 255);
		cfg.maxY = clamp(cfg.maxY, 0, 255);

		if (cfg.minY > cfg.maxY) {
			int t = cfg.minY;
			cfg.minY = cfg.maxY;
			cfg.maxY = t;
		}

		node.setValue(Config.TOKEN, cfg);
		return cfg.toImmutable();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		builder.append(cfg.blocks).append(cfg.replacements).append(cfg.minY).append(cfg.maxY);
	}

	@Override
	public boolean isReady(ChunkView view, Object config) {
		return view.areNeighborsLoaded();
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		final int maxX = max.getX(), maxY = Math.min(max.getY(), cfg.maxY), maxZ = max.getZ();

		for (int y = Math.max(min.getY(), cfg.minY); y <= maxY; y++) {
			for (int z = min.getZ(); z <= maxZ; z++) {
				for (int x = min.getX(); x <= maxX; x++) {
					BlockState b = view.getBlock(x, y, z);
					if (b.getType() == BlockTypes.AIR)
						continue;

					if (cfg.blocks.contains(b) && !view.isExposed(x, y, z))
						view.setBlock(x, y, z, cfg.replacements.get(r).value);
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
		@Setting(value = "MinY", comment = "The minimum Y of the section to obfuscate")
		public int minY = 0;
		@Setting(value = "MaxY", comment = "The maximum Y of the section to obfuscate")
		public int maxY = 255;

		public Immutable toImmutable() {
			return new Immutable(this.blocks.getAll(), WeightedList.of(this.replacements), this.minY, this.maxY);
		}

		public static final class Immutable {
			public final Set<BlockState> blocks;
			public final WeightedList<BlockState> replacements;
			public final int minY, maxY;

			public Immutable(Collection<BlockState> blocks, WeightedList<BlockState> replacements, int minY, int maxY) {
				this.blocks = ImmutableSet.copyOf(blocks);
				this.replacements = replacements;
				this.minY = minY;
				this.maxY = maxY;
			}
		}
	}
}
