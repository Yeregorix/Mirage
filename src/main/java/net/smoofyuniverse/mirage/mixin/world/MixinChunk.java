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

package net.smoofyuniverse.mirage.mixin.world;

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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.world.SpongeEmptyChunk;

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
		bindContainers();
	}

	public boolean bindContainers() {
		if (this.netChunk == null)
			return false;

		try {
			synchronized (this.containersLock) {
				this.netChunk.setContainers(this.storageArrays);
			}
			return true;
		} catch (Exception e) {
			Mirage.LOGGER.error("Failed to set containers of a network chunk", e);
			return false;
		}
	}

	@Inject(method = "setStorageArrays", at = @At("RETURN"))
	public void onSetStorageArrays(CallbackInfo ci) {
		if (bindContainers()) {
			try {
				if (this.netChunk.getState() != State.OBFUSCATED)
					this.netChunk.loadFromCacheNow();
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to load a network chunk from cache", e);
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
	public void setValidCacheDate(long value) {
		this.cacheDate = value;
	}

	@Override
	public void bindContainer(int index) {
		if (this.netChunk == null)
			return;

		synchronized (this.containersLock) {
			if (this.netChunk.needContainer(index)) {
				ExtendedBlockStorage storage = this.storageArrays[index];
				if (storage == null) {
					storage = new ExtendedBlockStorage(index << 4, this.world.provider.hasSkyLight());
					this.netChunk.setContainer(index, storage);
					this.storageArrays[index] = storage;
					generateSkylightMap();
				} else {
					this.netChunk.setContainer(index, storage);
					if (!storage.isEmpty())
						this.netChunk.setSaved(false);
				}
			}
		}
	}

	@SuppressWarnings("UnresolvedMixinReference")
	@Redirect(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;Lorg/spongepowered/api/block/BlockSnapshot;Lorg/spongepowered/api/world/BlockChangeFlag;)Lnet/minecraft/block/state/IBlockState;", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/ExtendedBlockStorage"), remap = false)
	public ExtendedBlockStorage onBlockChange_newStorage(int y, boolean storeSkylight) {
		return createStorage(y >> 4, storeSkylight);
	}

	@SuppressWarnings("UnresolvedMixinReference")
	@Redirect(method = "fill(Lnet/minecraft/world/chunk/ChunkPrimer;)V", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/ExtendedBlockStorage"), remap = false)
	public ExtendedBlockStorage onFill_newStorage(int y, boolean storeSkylight) {
		return createStorage(y >> 4, storeSkylight);
	}

	public ExtendedBlockStorage createStorage(int index, boolean storeSkylight) {
		ExtendedBlockStorage storage = new ExtendedBlockStorage(index << 4, storeSkylight);

		if (this.netChunk != null) {
			try {
				synchronized (this.containersLock) {
					if (this.netChunk.needContainer(index)) {
						this.netChunk.setContainer(index, storage);
						if (!storage.isEmpty())
							this.netChunk.setSaved(false);
					}
				}
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to set a container of a network chunk", e);
			}
		}

		return storage;
	}

	@Redirect(method = "setLightFor", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/ExtendedBlockStorage"))
	public ExtendedBlockStorage onLightChange_newStorage(int y, boolean storeSkylight) {
		return createStorage(y >> 4, storeSkylight);
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		checkBlockPosition(x, y, z);

		x &= 15;
		z &= 15;

		if (y != 255 && !isOpaque1(x, y + 1, z))
			return true;
		if (y != 0 && !isOpaque1(x, y - 1, z))
			return true;

		return !(isOpaque2(x + 1, y, z) && isOpaque2(x - 1, y, z) && isOpaque2(x, y, z + 1) && isOpaque2(x, y, z - 1));
	}

	private boolean isOpaque2(int x, int y, int z) {
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
			return isOpaque1(x, y, z);

		InternalChunk neighbor = ((InternalWorld) this.world).getChunk(this.x + dx, this.z + dz);
		return neighbor != null && ((InternalBlockState) ((Chunk) neighbor).getBlockState(x, y, z)).isOpaque();
	}

	private boolean isOpaque1(int x, int y, int z) {
		return ((InternalBlockState) getBlockState(x, y, z)).isOpaque();
	}

	@Override
	public int getBlockLightLevel(int x, int y, int z) {
		return getLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z));
	}

	@Override
	public boolean canSeeTheSky(int x, int y, int z) {
		return y >= getHeightValue(x & 15, z & 15);
	}

	@Override
	public boolean areNeighborsLoaded() {
		InternalWorld w = (InternalWorld) this.world;
		return w.isChunkLoaded(this.x + 1, this.z) && w.isChunkLoaded(this.x, this.z + 1) && w.isChunkLoaded(this.x - 1, this.z) && w.isChunkLoaded(this.x, this.z - 1);
	}
}
