package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.api.NetworkBlockContainer;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockStateContainer.class)
public abstract class MixinBlockStateContainer implements InternalBlockStateContainer {

	@Shadow
	public abstract IBlockState get(int x, int y, int z);

	@Shadow
	private static IBlockStatePalette REGISTRY_BASED_PALETTE;
	@Shadow
	protected static IBlockState AIR_BLOCK_STATE;

	private NetworkBlockContainer modifiedStorage = new NetworkBlockContainer(REGISTRY_BASED_PALETTE, AIR_BLOCK_STATE);

	@Override
	public void writeModified(PacketBuffer buf) {
		modifiedStorage.write(buf);
	}

	@Override
	public int modifiedSize() {
		return modifiedStorage.size();
	}

	@Override
	public void setY(int y) {
		modifiedStorage.setY(y);
	}

	@Inject(method = "set(IIILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
	public void setModified(int x, int y, int z, IBlockState blockState, CallbackInfo callbackInfo) {
		modifiedStorage.set(x, y, z, blockState);
	}

	@Inject(method = "set(ILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
	public void setModified(int index, IBlockState state, CallbackInfo callbackInfo) {
		modifiedStorage.set(index, state);
	}

	@Inject(method = "setBits", at = @At("HEAD"))
	private void setModifiedBits(int bitsIn, CallbackInfo callbackInfo) {
		modifiedStorage.setBits(bitsIn);
	}

	@Override
	public NetworkBlockContainer getBlockContainer() {
		return modifiedStorage;
	}
}
