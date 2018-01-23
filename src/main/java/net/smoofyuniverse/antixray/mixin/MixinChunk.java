/*
 * The MIT License (MIT)
 *
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

package net.smoofyuniverse.antixray.mixin;

import com.flowpowered.math.vector.Vector3i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk.State;
import net.smoofyuniverse.antixray.impl.network.NetworkWorld;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk implements InternalChunk {
	@Shadow
	@Final
	public int x;
	@Shadow
	@Final
	public int z;
	@Shadow
	@Final
	private ExtendedBlockStorage[] storageArrays;
	@Shadow
	@Final
	private World world;

	private NetworkChunk netChunk;
	private long cacheDate;

	@Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		NetworkWorld netWorld = ((InternalWorld) this.world).getView();
		if (netWorld.isEnabled())
			this.netChunk = new NetworkChunk(this, netWorld);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V", at = @At("RETURN"))
	public void onInitWithPrimer(CallbackInfo ci) {
		if (this.netChunk != null) {
			try {
				this.netChunk.setContainers(this.storageArrays);
			} catch (Exception e) {
				AntiXray.LOGGER.error("Failed to update containers of a network chunk", e);
			}
		}
	}

	@Nullable
	@Override
	public NetworkChunk getView() {
		return this.netChunk;
	}

	@Override
	public long getValidCacheDate() {
		return this.cacheDate;
	}

	@Override
	public InternalWorld getWorld() {
		return (InternalWorld) this.world;
	}

	@Override
	public void setValidCacheDate(long value) {
		this.cacheDate = value;
	}

	@Inject(method = "setStorageArrays", at = @At("RETURN"))
	public void onSetStorageArrays(CallbackInfo ci) {
		if (this.netChunk != null) {
			try {
				this.netChunk.setContainers(this.storageArrays);
				if (this.netChunk.getState() != State.OBFUSCATED)
					this.netChunk.loadFromCacheNow();
			} catch (Exception e) {
				AntiXray.LOGGER.error("Failed to update containers of a network chunk", e);
			}
		}
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			throw new PositionOutOfBoundsException(new Vector3i(x, y, z), getBlockMin(), getBlockMax());

		x &= 15;
		z &= 15;

		if (y != 255 && notFullCube1(x, y + 1, z))
			return true;
		if (y != 0 && notFullCube1(x, y - 1, z))
			return true;

		return notFullCube2(x + 1, y, z) || notFullCube2(x - 1, y, z) || notFullCube2(x, y, z + 1) || notFullCube2(x, y, z - 1);
	}

	private boolean notFullCube1(int x, int y, int z) {
		return !getBlockState(x, y, z).isFullCube();
	}

	private boolean notFullCube2(int x, int y, int z) {
		int dx = 0, dz = 0;
		if (x < 0) {
			dx--;
			x += 16;
		} else if (x >= 16) {
			dx++;
			x -= 16;
		}
		if (z < 0) {
			dz--;
			z += 16;
		} else if (z >= 16) {
			dz++;
			z -= 16;
		}

		if (dx == 0 && dz == 0)
			return notFullCube1(x, y, z);

		InternalChunk neighbor = ((InternalWorld) this.world).getChunk(this.x + dx, this.z + dz);
		return neighbor == null || !((Chunk) neighbor).getBlockState(x, y, z).isFullCube();
	}

	@Shadow
	public abstract IBlockState getBlockState(int x, int y, int z);

	@Override
	public int getBlockLightLevel(int x, int y, int z) {
		return getLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z));
	}

	@Override
	public boolean canSeeTheSky(int x, int y, int z) {
		return y >= getHeightValue(x & 15, z & 15);
	}

	@Shadow
	public abstract int getHeightValue(int x, int z);

	@Shadow
	public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

	@Override
	public boolean isExpositionCheckReady() {
		return isNeighborLoaded(Direction.NORTH) && isNeighborLoaded(Direction.SOUTH) && isNeighborLoaded(Direction.EAST) && isNeighborLoaded(Direction.WEST);
	}
}
