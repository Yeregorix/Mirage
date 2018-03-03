package net.smoofyuniverse.antixray.mixin.packet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SPacketMultiBlockChange.BlockUpdateData.class)
public class MixinBlockUpdateData {

	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), require = 0)
	public IBlockState onGetBlockState(Chunk chunk, BlockPos pos) {
		return (IBlockState) ((InternalChunk) chunk).getView().getBlock(pos.getX(), pos.getY(), pos.getZ());
	}
}
