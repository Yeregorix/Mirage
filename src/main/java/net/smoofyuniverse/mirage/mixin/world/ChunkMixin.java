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

package net.smoofyuniverse.mirage.mixin.world;

import com.flowpowered.math.vector.Vector2i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.world.SpongeEmptyChunk;

@Mixin(value = Chunk.class, priority = 1100)
public abstract class ChunkMixin implements InternalChunk {
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
	@Shadow
	private boolean dirty;

	private final Object containersLock = new Object();
	private NetworkChunk netChunk;
	private long cacheDate;

	@Shadow
	public abstract IBlockState getBlockState(int x, int y, int z);

	@Shadow
	public abstract int getHeightValue(int x, int z);

	@Shadow
	public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

	@Shadow
	public abstract void generateSkylightMap();

	@Shadow
	public boolean unloadQueued;

	@Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		if (((Object) this) instanceof SpongeEmptyChunk)
			return;

		NetworkWorld netWorld = ((InternalWorld) this.world).getView();
		if (netWorld.isEnabled())
			this.netChunk = new NetworkChunk(this, netWorld);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V", at = @At("RETURN"))
	public void onInitWithPrimer(CallbackInfo ci) {
		captureContainers();
	}

	@Override
	public boolean captureContainers() {
		if (this.netChunk == null)
			return false;

		try {
			synchronized (this.containersLock) {
				for (ExtendedBlockStorage storage : this.storageArrays)
					this.netChunk.captureContainer(storage);
			}
			return true;
		} catch (Exception e) {
			Mirage.LOGGER.error("Failed to capture the existing containers of a chunk", e);
			return false;
		}
	}

	@Override
	public void requireContainer(int index) {
		if (this.netChunk == null)
			return;

		synchronized (this.containersLock) {
			ExtendedBlockStorage storage = this.storageArrays[index];
			if (storage == null) {
				storage = new ExtendedBlockStorage(index << 4, this.world.provider.hasSkyLight());
				this.netChunk.captureContainer(storage);
				this.storageArrays[index] = storage;
				generateSkylightMap();
			} else {
				this.netChunk.captureContainer(storage);
			}
		}
	}

	@Override
	public NetworkChunk getView() {
		if (this.netChunk == null)
			throw new IllegalStateException("NetworkChunk not available");
		return this.netChunk;
	}

	@Override
	public boolean isViewAvailable() {
		return this.netChunk != null;
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
	public void markActive() {
		this.unloadQueued = false;
	}

	@Override
	public void setValidCacheDate(long value) {
		this.cacheDate = value;
	}

	@Inject(method = "setStorageArrays", at = @At("RETURN"))
	public void onSetStorageArrays(CallbackInfo ci) {
		if (captureContainers()) {
			try {
				if (this.netChunk.getState() != State.OBFUSCATED)
					this.netChunk.loadFromCacheNow();
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to load a network chunk from cache", e);
			}
		}
	}

	@Dynamic(mixin = org.spongepowered.common.mixin.core.world.chunk.ChunkMixin.class)
	@ModifyVariable(method = "bridge$setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;Lorg/spongepowered/api/world/BlockChangeFlag;)Lnet/minecraft/block/state/IBlockState;", at = @At(value = "STORE", ordinal = 1), remap = false)
	public ExtendedBlockStorage onBlockChange_newStorage(ExtendedBlockStorage storage) {
		captureStorage(storage);
		return storage;
	}

	private void captureStorage(ExtendedBlockStorage storage) {
		if (this.netChunk != null) {
			try {
				synchronized (this.containersLock) {
					this.netChunk.captureContainer(storage);
				}
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to capture a container of a chunk", e);
			}
		}
	}

	@Dynamic(mixin = org.spongepowered.common.mixin.core.world.chunk.ChunkMixin.class)
	@Redirect(method = "bridge$fill(Lnet/minecraft/world/chunk/ChunkPrimer;)V", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/ExtendedBlockStorage"), remap = false)
	public ExtendedBlockStorage onFill_newStorage(int y, boolean storeSkylight) {
		ExtendedBlockStorage storage = new ExtendedBlockStorage(y, storeSkylight);
		captureStorage(storage);
		return storage;
	}

	@ModifyVariable(method = "setLightFor", at = @At(value = "STORE", ordinal = 1))
	public ExtendedBlockStorage onLightChange_newStorage(ExtendedBlockStorage storage) {
		captureStorage(storage);
		return storage;
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		checkBlockPosition(x, y, z);

		x &= 15;
		z &= 15;

		// y + 1
		if (y == 255 || !isOpaque(x, y + 1, z))
			return true;

		// y - 1
		if (y == 0 || !isOpaque(x, y - 1, z))
			return true;

		// x + 1
		if (x == 15) {
			InternalChunk c = ((InternalWorld) this.world).getChunk(this.x + 1, this.z);
			if (c == null || !c.isOpaque(0, y, z))
				return true;
		} else if (!isOpaque(x + 1, y, z))
			return true;

		// x - 1
		if (x == 0) {
			InternalChunk c = ((InternalWorld) this.world).getChunk(this.x - 1, this.z);
			if (c == null || !c.isOpaque(15, y, z))
				return true;
		} else if (!isOpaque(x - 1, y, z))
			return true;

		// z + 1
		if (z == 15) {
			InternalChunk c = ((InternalWorld) this.world).getChunk(this.x, this.z + 1);
			if (c == null || !c.isOpaque(x, y, 0))
				return true;
		} else if (!isOpaque(x, y, z + 1))
			return true;

		// z - 1
		if (z == 0) {
			InternalChunk c = ((InternalWorld) this.world).getChunk(this.x, this.z - 1);
			if (c == null || !c.isOpaque(x, y, 15))
				return true;
		} else if (!isOpaque(x, y, z - 1))
			return true;

		return false;
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		ExtendedBlockStorage storage = this.storageArrays[y >> 4];
		return storage != null && ((InternalBlockState) storage.get(x & 15, y & 15, z & 15)).isOpaque();
	}

	@Override
	public Vector2i getLightLevels(int x, int y, int z) {
		checkBlockPosition(x, y, z);

		x &= 15;
		z &= 15;

		ExtendedBlockStorage storage = this.storageArrays[y >> 4];
		if (storage == null)
			return y >= getHeightValue(x & 15, z & 15) ? new Vector2i(0, 15) : Vector2i.ZERO;

		return new Vector2i(storage.getBlockLight(x, y & 15, z), this.world.provider.hasSkyLight() ? storage.getSkyLight(x, y & 15, z) : 0);
	}

	@Override
	public int getHighestY(int x, int z) {
		checkBlockPosition(x, 0, z);
		return getHeightValue(x & 15, z & 15);
	}

	@Override
	public boolean areNeighborsLoaded() {
		InternalWorld w = (InternalWorld) this.world;
		return w.isChunkLoaded(this.x + 1, this.z) && w.isChunkLoaded(this.x, this.z + 1) && w.isChunkLoaded(this.x - 1, this.z) && w.isChunkLoaded(this.x, this.z - 1);
	}
}
