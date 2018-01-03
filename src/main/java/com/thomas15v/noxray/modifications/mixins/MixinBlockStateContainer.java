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

import com.thomas15v.noxray.api.NetworkBlockContainer;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockStateContainer.class)
public abstract class MixinBlockStateContainer implements InternalBlockStateContainer {

	@Shadow
	@Final
	protected static IBlockState AIR_BLOCK_STATE;
	@Shadow
	@Final
	private static IBlockStatePalette REGISTRY_BASED_PALETTE;

	@Shadow
	public abstract IBlockState get(int x, int y, int z);

	private NetworkBlockContainer modifiedStorage = new NetworkBlockContainer(REGISTRY_BASED_PALETTE, AIR_BLOCK_STATE);

	@Override
	public int modifiedSize() {
		return this.modifiedStorage.size();
	}

	@Override
	public void writeModified(PacketBuffer buf) {
		this.modifiedStorage.write(buf);
	}

	@Override
	public void setY(int y) {
		this.modifiedStorage.setY(y);
	}

	@Override
	public NetworkBlockContainer getBlockContainer() {
		return this.modifiedStorage;
	}

	@Inject(method = "set(IIILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
	public void setModified(int x, int y, int z, IBlockState blockState, CallbackInfo callbackInfo) {
		this.modifiedStorage.set(x, y, z, blockState);
	}

	@Inject(method = "set(ILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
	public void setModified(int index, IBlockState state, CallbackInfo callbackInfo) {
		this.modifiedStorage.set(index, state);
	}

	@Inject(method = "setBits", at = @At("HEAD"))
	private void setModifiedBits(int bitsIn, CallbackInfo callbackInfo) {
		this.modifiedStorage.setBits(bitsIn);
	}
}
