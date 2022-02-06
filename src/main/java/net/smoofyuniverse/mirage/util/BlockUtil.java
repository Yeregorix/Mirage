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

package net.smoofyuniverse.mirage.util;

import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.fluid.FluidState;
import org.spongepowered.api.fluid.FluidTypes;
import org.spongepowered.api.world.volume.block.BlockVolume;
import org.spongepowered.api.world.volume.block.BlockVolume.Mutable;
import org.spongepowered.api.world.volume.stream.StreamOptions;
import org.spongepowered.api.world.volume.stream.VolumeElement;
import org.spongepowered.api.world.volume.stream.VolumeStream;
import org.spongepowered.common.world.volume.SpongeVolumeStream;
import org.spongepowered.common.world.volume.VolumeStreamUtils;
import org.spongepowered.common.world.volume.buffer.block.ArrayMutableBlockBuffer;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class BlockUtil {
	public static final BlockState AIR = BlockTypes.AIR.get().defaultState(),
			BEDROCK = BlockTypes.BEDROCK.get().defaultState();

	public static final FluidState NO_FLUID = FluidTypes.EMPTY.get().defaultState();

	public static boolean isOpaque(BlockState state) {
		return ((InternalBlockState) state).isOpaque();
	}

	public static VolumeStream<BlockVolume.Mutable, BlockState> blockStateStream(BlockVolume.Mutable volume, Vector3i min, Vector3i max, StreamOptions options) {
		VolumeStreamUtils.validateStreamArgs(min, max, volume.min(), volume.max(), options);

		BlockVolume.Mutable buffer;
		if (options.carbonCopy()) {
			buffer = new ArrayMutableBlockBuffer(min, max.sub(min).add(Vector3i.ONE));
			copy(volume, buffer, min, max);
		} else {
			buffer = volume;
		}

		Stream<VolumeElement<Mutable, BlockState>> stateStream = IntStream.range(min.x(), max.x() + 1)
				.mapToObj(x -> IntStream.range(min.z(), max.z() + 1)
						.mapToObj(z -> IntStream.range(min.y(), max.y() + 1)
								.mapToObj(y -> VolumeElement.of(volume, () -> buffer.block(x, y, z), new Vector3d(x, y, z)))
						).flatMap(Function.identity())
				).flatMap(Function.identity());

		return new SpongeVolumeStream<>(stateStream, () -> volume);
	}

	public static void copy(BlockVolume from, BlockVolume.Mutable to, Vector3i min, Vector3i max) {
		int minX = min.x(), minY = min.y(), minZ = min.z();
		int maxX = max.x(), maxY = max.y(), maxZ = max.z();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					to.setBlock(x, y, z, from.block(x, y, z));
				}
			}
		}
	}
}
