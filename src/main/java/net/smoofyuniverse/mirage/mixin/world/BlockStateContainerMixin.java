/*
 * Copyright (c) 2018-2020 Hugo Dupanloup (Yeregorix)
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
import net.minecraft.world.chunk.BlockStateContainer;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.mirage.impl.network.NetworkBlockContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockStateContainer.class)
public class BlockStateContainerMixin implements InternalBlockContainer {
	private final NetworkBlockContainer networkContainer = new NetworkBlockContainer((BlockStateContainer) (Object) this);

	@Override
	public NetworkBlockContainer getNetworkBlockContainer() {
		return this.networkContainer;
	}

	@Inject(method = "set(ILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
	public void onSet(int index, IBlockState state, CallbackInfo ci) {
		this.networkContainer.set(index, state);
	}

	@Inject(method = "setBits(I)V", at = @At("HEAD"))
	public void onSetBits(int bits, CallbackInfo ci) {
		this.networkContainer.setBits(bits);
	}
}
