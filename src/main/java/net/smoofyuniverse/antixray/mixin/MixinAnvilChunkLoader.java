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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(AnvilChunkLoader.class)
public class MixinAnvilChunkLoader {
	private InternalWorld world;

	@Inject(method = "writeChunkToNBT", at = @At("RETURN"))
	public void onWriteChunkToNBT(Chunk chunk, World world, NBTTagCompound compound, CallbackInfo ci) {
		if (this.world == null)
			this.world = (InternalWorld) world;
		else if (this.world != world)
			AntiXray.LOGGER.warn("World change detected in an AnvilChunkLoader! This is going to generate caching errors");

		try {
			InternalChunk internalChunk = (InternalChunk) chunk;
			if (internalChunk.isViewAvailable()) {
				internalChunk.getView().saveToCacheLater();
				compound.setLong("AntiXrayCacheDate", internalChunk.getValidCacheDate());
			}
		} catch (Exception e) {
			AntiXray.LOGGER.error("Failed to serialize a network chunk for caching", e);
		}
	}

	@Inject(method = "readChunkFromNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NBTTagCompound;getIntArray(Ljava/lang/String;)[I", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
	public void onReadChunkFromNBT(World world, NBTTagCompound compound, CallbackInfoReturnable<Chunk> ci, int cx, int cz, Chunk chunk) {
		((InternalChunk) chunk).setValidCacheDate(compound.getLong("AntiXrayCacheDate"));
	}

	@Inject(method = "writeChunkData", at = @At("RETURN"))
	public void onWriteChunkData(ChunkPos pos, NBTTagCompound compound, CallbackInfo ci) {
		try {
			this.world.getView().savePendingChunk(pos.x, pos.z);
		} catch (Exception e) {
			AntiXray.LOGGER.error("Failed to save a pending network chunk", e);
		}
	}
}
