package net.smoofyuniverse.antixray.mixin.packet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SPacketBlockChange.class)
public class MixinSPacketBlockChange {

	@Redirect(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
	public IBlockState onGetBlockState(World world, BlockPos pos) {
		return (IBlockState) ((InternalWorld) world).getView().getBlock(pos.getX(), pos.getY(), pos.getZ());
	}
}
