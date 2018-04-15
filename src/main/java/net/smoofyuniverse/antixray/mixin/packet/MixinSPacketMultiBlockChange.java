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

package net.smoofyuniverse.antixray.mixin.packet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange.BlockUpdateData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalMultiBlockChange;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SPacketMultiBlockChange.class)
public class MixinSPacketMultiBlockChange implements InternalMultiBlockChange {
	@Shadow
	public BlockUpdateData[] changedBlocks;

	@Shadow
	public ChunkPos chunkPos;

	@Inject(method = "<init>(I[SLnet/minecraft/world/chunk/Chunk;)V", at = @At("RETURN"))
	public void onInit(int changes, short[] offsets, Chunk chunk, CallbackInfo ci) {
		if (((InternalChunk) chunk).isViewAvailable()) {
			NetworkChunk netChunk = ((InternalChunk) chunk).getView();
			SPacketMultiBlockChange thisObj = (SPacketMultiBlockChange) (Object) this;

			for (int i = 0; i < this.changedBlocks.length; i++) {
				short offset = offsets[i];
				this.changedBlocks[i] = thisObj.new BlockUpdateData(offset,
						(IBlockState) netChunk.getBlock(offset >> 12 & 15, offset & 255, offset >> 8 & 15));
			}
		}
	}

	@Override
	public void fastInit(int changes, short[] offsets, Chunk chunk) {
		this.chunkPos = new ChunkPos(chunk.x, chunk.z);
		this.changedBlocks = new BlockUpdateData[changes];

		SPacketMultiBlockChange thisObj = (SPacketMultiBlockChange) (Object) this;

		if (((InternalChunk) chunk).isViewAvailable()) {
			NetworkChunk netChunk = ((InternalChunk) chunk).getView();

			for (int i = 0; i < this.changedBlocks.length; i++) {
				short offset = offsets[i];
				this.changedBlocks[i] = thisObj.new BlockUpdateData(offset,
						(IBlockState) netChunk.getBlock(offset >> 12 & 15, offset & 255, offset >> 8 & 15));
			}
		} else {
			int bx = chunk.x << 4, bz = chunk.z << 4;

			for (int i = 0; i < this.changedBlocks.length; i++) {
				short offset = offsets[i];
				this.changedBlocks[i] = thisObj.new BlockUpdateData(offset,
						chunk.getBlockState(bx + (offset >> 12 & 15), offset & 255, bz + (offset >> 8 & 15)));
			}
		}
	}
}
