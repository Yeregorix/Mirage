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

package net.smoofyuniverse.antixray.mixin.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.antixray.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SPacketChunkData.class)
public abstract class MixinSPacketChunkData {

	@Inject(method = "calculateChunkSize", at = @At("HEAD"))
	public void onCalculateChunkSize(Chunk chunk, boolean skyLight, int sections, CallbackInfoReturnable<Integer> ci) {
		if (((InternalChunk) chunk).isViewAvailable())
			((InternalChunk) chunk).getView().obfuscate();
	}

	@Redirect(method = "extractChunkData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockStateContainer;write(Lnet/minecraft/network/PacketBuffer;)V"))
	public void writeModified(BlockStateContainer container, PacketBuffer buffer) {
		((InternalBlockContainer) container).getNetworkBlockContainer().write(buffer);
	}

	@Redirect(method = "calculateChunkSize", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockStateContainer;getSerializedSize()I"))
	public int calculateModifiedSize(BlockStateContainer container) {
		return ((InternalBlockContainer) container).getNetworkBlockContainer().getSerializedSize();
	}
}
