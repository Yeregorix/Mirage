/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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

package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import com.thomas15v.noxray.modifications.internal.InternalChunk;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SPacketChunkData.class)
public class MixinPacketChunkData {

	@Redirect(method = "extractChunkData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockStateContainer;write(Lnet/minecraft/network/PacketBuffer;)V"))
	public void writeModified(BlockStateContainer storage, PacketBuffer buffer, PacketBuffer methodbuffer, Chunk chunk, boolean b, int i) {
		((InternalBlockStateContainer) storage).writeModified(buffer);
	}

	@Redirect(method = "calculateChunkSize", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockStateContainer;getSerializedSize()I"))
	public int calculateModifiedSize(BlockStateContainer storage, Chunk chunk, boolean b, int i) {
		((InternalChunk) chunk).obfuscateBlocks();
		return ((InternalBlockStateContainer) storage).modifiedSize();
	}
}
