package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.api.NetworkBlockContainer;
import com.thomas15v.noxray.api.NetworkChunk;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Chunk.class)
public class MixinChunk {

    @Shadow
    public int xPosition;
    @Shadow
    public int zPosition;
    @Shadow
    private World worldObj;

    private NetworkChunk networkChunk;

    @Inject(method = "setStorageArrays", at = @At("HEAD"))
    public void setChunkData(ExtendedBlockStorage[] newStorageArrays, CallbackInfo info){
        //support for multiworlds (forge or vanilla) I don't see a reason to obfuscateInnerBlocks the end or the nether
        if (worldObj.getWorldType().getWorldTypeID() != -1 && worldObj.getWorldType().getWorldTypeID() != 1 && networkChunk == null) {
            NetworkBlockContainer[] blockContainers = new NetworkBlockContainer[newStorageArrays.length];
            for (int i = 0; i < newStorageArrays.length; i++) {
                if (newStorageArrays[i] != null){
                    blockContainers[i] = ((InternalBlockStateContainer) newStorageArrays[i].getData()).getBlockContainer();
                }

            }
            networkChunk = new NetworkChunk(blockContainers, (org.spongepowered.api.world.Chunk) this);
            ((InternalWorld)worldObj).getNetworkWorld().addChunk(networkChunk);
            networkChunk.obfuscateInnerBlocks();
        }
    }

    /*@Debug(print = true)
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;" +
            "Lnet/minecraft/block/state/IBlockState;Lorg/spongepowered/api/block/BlockSnapshot;)Lnet/minecraft/block/state/IBlockState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;set(IIILnet/minecraft/block/state/IBlockState;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void setChunkData(BlockPos pos, IBlockState state, IBlockState currentState, BlockSnapshot newBlockSnapshot,
                             CallbackInfoReturnable<IBlockState> cir, ExtendedBlockStorage  extendedblockstorage ){
        for (ExtendedBlockStorage storageArray : storageArrays) {
            ((InternalChunkData)storageArray).setChunkX(xPosition).setChunkZ(zPosition);
        }
    }*/

}
