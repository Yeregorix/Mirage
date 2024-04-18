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

import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.config.resources.Resources;
import net.smoofyuniverse.mirage.modifier.RandomBedrockModifier.Config.Resolved;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.api.world.WorldTypes;
import org.spongepowered.api.world.server.storage.ServerWorldProperties;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.math.vector.Vector3i;

import java.util.Random;

import static net.smoofyuniverse.mirage.util.BlockUtil.BEDROCK;
import static net.smoofyuniverse.mirage.util.RegistryUtil.resolveBlockState;

public class RandomBedrockModifier implements ChunkModifier {

	@Override
	public boolean requireCache() {
		return false;
	}

	@Override
	public boolean isCompatible(ServerWorldProperties properties) {
		return properties.worldType() != WorldTypes.THE_END.get();
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldType worldType, String preset) throws SerializationException {
		Config cfg = node.get(Config.class);
		if (cfg == null)
			cfg = new Config();

		if (cfg.ground == null)
			cfg.ground = Resources.of(worldType).ground;
		if (cfg.height < 0)
			cfg.height = 0;

		node.set(cfg);
		return cfg.resolve();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {
		Resolved cfg = (Resolved) config;
		builder.append(cfg.ground).append(cfg.height);
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Resolved cfg = (Resolved) config;

		int height = cfg.height;

		int floorY = view.min().y();
		int maxY = Math.min(max.y(), floorY + height - 1);
		if (maxY < min.y())
			return;

		for (int x = min.x(); x <= max.x(); x++) {
			for (int z = min.z(); z <= max.z(); z++) {
				for (int y = floorY; y <= maxY; y++) {
					if (view.isExposed(x, y, z))
						continue;

					if ((y - floorY) <= r.nextInt(height)) {
						view.setBlock(x, y, z, BEDROCK);
					} else if (view.block(x, y, z) == BEDROCK) {
						view.setBlock(x, y, z, cfg.ground);
					}
				}
			}
		}
	}

	@org.spongepowered.configurate.objectmapping.ConfigSerializable
	public static final class Config {
		@Comment("The ground type used to hide real bedrock")
		@Setting("Ground")
		public String ground;

		@Comment("The maximum layer where bedrock can be generated")
		@Setting("Height")
		public int height = 5;

		public Resolved resolve() {
			return new Resolved(resolveBlockState(this.ground), this.height);
		}

		public static final class Resolved {
			public final BlockState ground;
			public final int height;

			public Resolved(BlockState ground, int height) {
				this.ground = ground;
				this.height = height;
			}
		}
	}
}
