/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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
import com.google.common.reflect.TypeToken;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.resource.Resources;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Random;

public class BedrockModifier extends ChunkModifier {

	public BedrockModifier() {
		super(Mirage.get(), "Bedrock");
	}

	@Override
	public boolean shouldCache() {
		return false;
	}

	@Override
	public boolean isCompatible(WorldProperties world) {
		return world.getDimensionType() != DimensionTypes.THE_END;
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldProperties world, String preset) throws ObjectMappingException {
		Config cfg = node.getValue(Config.TOKEN);
		if (cfg == null)
			cfg = new Config();

		if (cfg.ground == null)
			cfg.ground = Resources.of(world).getGround();
		if (cfg.height < 0)
			cfg.height = 0;

		node.setValue(Config.TOKEN, cfg);
		return cfg.toImmutable();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		builder.append(cfg.ground).append(cfg.height);
	}

	@Override
	public boolean isReady(ChunkView view, Object config) {
		return view.areNeighborsLoaded();
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;

		int height = Math.min(max.getY(), cfg.height);
		if (height == 0)
			return;

		for (int x = min.getX(); x <= max.getX(); x++) {
			for (int z = min.getZ(); z <= max.getZ(); z++) {
				for (int y = height; y >= 0; --y) {
					if (view.isExposed(x, y, z))
						continue;

					if (y <= r.nextInt(height)) {
						view.setBlockType(x, y, z, BlockTypes.BEDROCK);
					} else if (view.getBlockType(x, y, z) == BlockTypes.BEDROCK) {
						view.setBlock(x, y, z, cfg.ground);
					}
				}
			}
		}
	}

	@ConfigSerializable
	public static final class Config {
		public static final TypeToken<Config> TOKEN = TypeToken.of(Config.class);

		@Setting(value = "Ground", comment = "The ground type used to hide real bedrock")
		public BlockState ground;
		@Setting(value = "Height", comment = "The maximum layer where bedrock can be generated")
		public int height = 5;

		public Config.Immutable toImmutable() {
			return new Config.Immutable(this.ground, this.height);
		}

		public static final class Immutable {
			public final BlockState ground;
			public final int height;

			public Immutable(BlockState ground, int height) {
				this.ground = ground;
				this.height = height;
			}
		}
	}
}
