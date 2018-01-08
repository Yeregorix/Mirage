/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.noxray.modifications.mixins;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.smoofyuniverse.noxray.api.BlockStorage;
import net.smoofyuniverse.noxray.api.NetworkWorld;
import net.smoofyuniverse.noxray.modifications.internal.InternalWorld;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public abstract class MixinWorld implements InternalWorld, BlockStorage {
	@Shadow
	protected IChunkProvider chunkProvider;

	private final NetworkWorld networkWorld = new NetworkWorld((org.spongepowered.api.world.World) this);
	private DimensionType dimensionType;
	private BlockType groundType;

	@Override
	public boolean isExposed(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			return false;

		if (y != 256 && notFullCube(x, y + 1, z))
			return true;
		if (y != 0 && notFullCube(x, y - 1, z))
			return true;

		return notFullCube(x + 1, y, z) || notFullCube(x - 1, y, z) || notFullCube(x, y, z + 1) || notFullCube(x, y, z - 1);
	}

	private boolean notFullCube(int x, int y, int z) {
		Chunk chunk = this.chunkProvider.getLoadedChunk(x >> 4, z >> 4);
		return chunk != null && !chunk.getBlockState(x, y, z).isFullCube();
	}

	@Override
	public int getBlockLightLevel(int x, int y, int z) {
		return getLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z));
	}

	@Shadow
	public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

	@Override
	public NetworkWorld getNetworkWorld() {
		return this.networkWorld;
	}

	@Override
	public boolean canSeeTheSky(int x, int y, int z) {
		return canSeeSky(new BlockPos(x, y, z));
	}

	@Shadow
	public abstract boolean canSeeSky(BlockPos pos);
}
