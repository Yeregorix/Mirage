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

package net.smoofyuniverse.mirage.mixin.chunk;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelChunk.class, priority = 1200)
public abstract class LevelChunkMixin extends ChunkAccessMixin implements InternalChunk {
	@Shadow
	@Final
	Level level;

	private NetworkChunk netChunk;

	@Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
			at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		if (((Object) this) instanceof EmptyLevelChunk)
			return;

		NetworkWorld netWorld = ((InternalWorld) this.level).view();
		if (netWorld.isEnabled()) {
			this.netChunk = new NetworkChunk(this, netWorld);
		}
	}

	@Override
	public NetworkChunk view() {
		if (this.netChunk == null)
			throw new IllegalStateException("NetworkChunk not available");
		return this.netChunk;
	}

	@Override
	public InternalWorld world() {
		return (InternalWorld) this.level;
	}

	@Override
	public boolean isViewAvailable() {
		return this.netChunk != null;
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
			InternalChunk c = ((InternalWorld) this.level).opaqueChunk(this.chunkPos.x + 1, this.chunkPos.z);
			if (c == null || !c.isOpaque(0, y, z))
				return true;
		} else if (!isOpaque(x + 1, y, z))
			return true;

		// x - 1
		if (x == 0) {
			InternalChunk c = ((InternalWorld) this.level).opaqueChunk(this.chunkPos.x - 1, this.chunkPos.z);
			if (c == null || !c.isOpaque(15, y, z))
				return true;
		} else if (!isOpaque(x - 1, y, z))
			return true;

		// z + 1
		if (z == 15) {
			InternalChunk c = ((InternalWorld) this.level).opaqueChunk(this.chunkPos.x, this.chunkPos.z + 1);
			if (c == null || !c.isOpaque(x, y, 0))
				return true;
		} else if (!isOpaque(x, y, z + 1))
			return true;

		// z - 1
		if (z == 0) {
			InternalChunk c = ((InternalWorld) this.level).opaqueChunk(this.chunkPos.x, this.chunkPos.z - 1);
			if (c == null || !c.isOpaque(x, y, 15))
				return true;
		} else if (!isOpaque(x, y, z - 1))
			return true;

		return false;
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		LevelChunkSection section = this.sections[y >> 4];
		return section != null && ((InternalBlockState) section.getBlockState(x & 15, y & 15, z & 15)).isOpaque();
	}

	@Override
	public boolean areNeighborsLoaded() {
		InternalWorld w = (InternalWorld) this.level;
		int chunkX = this.chunkPos.x, chunkZ = this.chunkPos.z;
		return w.isChunkLoaded(chunkX + 1, chunkZ) && w.isChunkLoaded(chunkX, chunkZ + 1) && w.isChunkLoaded(chunkX - 1, chunkZ) && w.isChunkLoaded(chunkX, chunkZ - 1);
	}
}
