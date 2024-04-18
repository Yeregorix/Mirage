/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.network.NetworkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements InternalSection {
	@Shadow
	@Final
	private PalettedContainer<BlockState> states;

	private NetworkSection networkSection;

	@Shadow
	private PalettedContainerRO<Holder<Biome>> biomes;

	@Shadow
	public abstract void write(FriendlyByteBuf param0);

	@Shadow
	public abstract int getSerializedSize();

	@Shadow
	public abstract boolean hasOnlyAir();

	@Override
	public NetworkSection view() {
		if (this.networkSection == null) {
			this.networkSection = new NetworkSection((LevelChunkSection) (Object) this);
			if (!hasOnlyAir())
				this.networkSection.deobfuscate(null);
		}
		return this.networkSection;
	}

	@Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
	public void onSet(int x, int y, int z, BlockState state, boolean lock, CallbackInfoReturnable<BlockState> cir) {
		if (this.networkSection != null)
			this.networkSection.setBlockState(x, y, z, state);
	}

	@Override
	public void _write(FriendlyByteBuf buffer) {
		if (this.networkSection == null) {
			write(buffer);
		} else {
			this.networkSection.write(buffer);
			this.biomes.write(buffer);
		}
	}

	@Override
	public int _getSerializedSize() {
		return this.networkSection == null ? getSerializedSize() : (this.networkSection.getSerializedSize() + this.biomes.getSerializedSize());
	}
}
