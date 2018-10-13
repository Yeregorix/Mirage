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

package net.smoofyuniverse.mirage.modifier;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.api.volume.WorldView;
import net.smoofyuniverse.mirage.resource.Resources;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.gen.Populator;
import org.spongepowered.api.world.gen.WorldGenerator;
import org.spongepowered.api.world.gen.populator.Ore;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.*;
import java.util.function.Predicate;

import static com.flowpowered.math.GenericMath.floor;
import static com.flowpowered.math.TrigMath.cos;
import static com.flowpowered.math.TrigMath.sin;
import static net.smoofyuniverse.mirage.resource.Categories.COMMON;
import static net.smoofyuniverse.mirage.util.MathUtil.clamp;
import static net.smoofyuniverse.mirage.util.MathUtil.squared;

/**
 * This modifier does not hide anything but generates fake ores
 */
public class FakeGenModifier extends ChunkModifier {
	private static final float PI = (float) Math.PI;

	public FakeGenModifier() {
		super(Mirage.get(), "FakeGen");
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldProperties world, String preset) throws ObjectMappingException {
		Config cfg = node.getValue(Config.TOKEN);
		if (cfg == null)
			cfg = new Config();

		if (cfg.blocks == null)
			cfg.blocks = Resources.of(world).getBlocks(COMMON);

		cfg.density = clamp(cfg.density, 0.1, 10);
		cfg.dynamism = clamp(cfg.dynamism, 0, 10);
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
		builder.append(cfg.blocks).append(cfg.density).append(cfg.dynamism).append(cfg.minY).append(cfg.maxY);
	}

	@Override
	public boolean isReady(ChunkView view, Object config) {
		return view.areNeighborsLoaded();
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {
		Config.Immutable cfg = (Config.Immutable) config;
		boolean useDynamism = cfg.dynamism != 0 && view.isDynamismEnabled();

		final int minX = min.getX(), minY = Math.max(min.getY(), cfg.minY), minZ = min.getZ();
		final int maxX = max.getX(), maxY = Math.min(max.getY(), cfg.maxY), maxZ = max.getZ();

		if (minY > maxY)
			return;

		// Get world
		WorldView worldView;
		if (view instanceof WorldView)
			worldView = (WorldView) view;
		else if (view instanceof ChunkView)
			worldView = ((ChunkView) view).getWorld();
		else
			throw new UnsupportedOperationException();

		World world = (World) worldView.getStorage();

		// List ores
		WorldGenerator generator = world.getWorldGenerator();
		List<Ore> ores = new ArrayList<>();

		for (Populator pop : generator.getPopulators()) {
			if (pop instanceof Ore && cfg.blocks.contains(((Ore) pop).getOreBlock()))
				ores.add((Ore) pop);
		}

		List<BiomeType> biomes = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BiomeType biome = world.getBiome(x, 0, z);
				if (!biomes.contains(biome))
					biomes.add(biome);
			}
		}

		for (BiomeType biome : biomes) {
			for (Populator pop : generator.getBiomeSettings(biome).getPopulators()) {
				if (pop instanceof Ore && cfg.blocks.contains(((Ore) pop).getOreBlock()))
					ores.add((Ore) pop);
			}
		}

		// Simulate generation
		int sizeX = maxX - minX + 1, sizeZ = maxZ - minZ + 1;
		double factor = cfg.density * sizeX * sizeZ / 256d;

		int worldMinY = worldView.getBlockMin().getY(), worldMaxY = worldView.getBlockMax().getY();

		for (Ore ore : ores) {
			double amountD = ore.getDepositsPerChunk().getAmount(r) * factor;
			int amountI = (int) amountD;

			double dif = amountD - (double) amountI;
			if (dif != 0f && r.nextDouble() < dif)
				amountI++;

			Predicate<BlockState> predicate = ore.getPlacementCondition();
			BlockState block = ore.getOreBlock();

			for (int n = 0; n < amountI; n++) {
				int posX = minX + r.nextInt(sizeX), posY = ore.getHeight().getFlooredAmount(r), posZ = minZ + r.nextInt(sizeZ);
				if (posY < minY || posY > maxY)
					continue;

				int size = ore.getDepositSize().getFlooredAmount(r);
				float yaw = r.nextFloat() * PI;

				float sizeF = (float) size;
				float startX = (float) posX + sin(yaw) * sizeF / 8f, endX = (float) posX - sin(yaw) * sizeF / 8f;
				float startZ = (float) posZ + cos(yaw) * sizeF / 8f, endZ = (float) posZ - cos(yaw) * sizeF / 8f;
				float startY = posY + r.nextInt(3) - 2, endY = posY + r.nextInt(3) - 2;

				for (int i = 0; i < size; ++i) {
					float progress = i / sizeF;

					float centerX = startX + (endX - startX) * progress;
					float centerY = startY + (endY - startY) * progress;
					float centerZ = startZ + (endZ - startZ) * progress;

					float radius = ((sin(PI * progress) + 1f) * r.nextFloat() * sizeF / 16f + 1f) / 2f, radius2 = squared(radius);

					int fromX = floor(centerX - radius), fromY = Math.max(floor(centerY - radius), worldMinY), fromZ = floor(centerZ - radius);
					int toX = floor(centerX + radius), toY = Math.min(floor(centerY + radius), worldMaxY), toZ = floor(centerZ + radius);

					for (int x = fromX; x <= toX; ++x) {
						float dx2 = squared((float) x + 0.5f - centerX);

						if (dx2 < radius2) {
							for (int y = fromY; y <= toY; ++y) {
								float dy2 = squared((float) y + 0.5f - centerY);

								if (dx2 + dy2 < radius2) {
									for (int z = fromZ; z <= toZ; ++z) {
										float dz2 = squared((float) z + 0.5f - centerZ);

										if (dx2 + dy2 + dz2 < radius2 && predicate.test(worldView.getBlock(x, y, z))) {
											if (worldView.isExposed(x, y, z)) {
												if (useDynamism) {
													worldView.setDynamism(x, y, z, cfg.dynamism);
													worldView.setBlock(x, y, z, block);
												}
											} else {
												worldView.setBlock(x, y, z, block);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	@ConfigSerializable
	public static final class Config {
		public static final TypeToken<Config> TOKEN = TypeToken.of(Config.class);

		@Setting(value = "Blocks", comment = "Blocks that will be generated by the modifier")
		public BlockSet blocks;
		@Setting(value = "Density", comment = "The density of generated blocks, between 0.1 and 10")
		public double density = 1d;
		@Setting(value = "Dynamism", comment = "The dynamic obfuscation distance, between 0 and 10")
		public int dynamism = 4;
		@Setting(value = "MinY", comment = "The minimum Y of the section to obfuscate")
		public int minY = 0;
		@Setting(value = "MaxY", comment = "The maximum Y of the section to obfuscate")
		public int maxY = 128;

		public Immutable toImmutable() {
			return new Immutable(this.blocks.getAll(), this.density, this.dynamism, this.minY, this.maxY);
		}

		public static final class Immutable {
			public final Set<BlockState> blocks;
			public final double density;
			public final int dynamism;
			public final int minY, maxY;

			public Immutable(Collection<BlockState> blocks, double density, int dynamism, int minY, int maxY) {
				this.blocks = ImmutableSet.copyOf(blocks);
				this.density = density;
				this.dynamism = dynamism;
				this.minY = minY;
				this.maxY = maxY;
			}
		}
	}
}
