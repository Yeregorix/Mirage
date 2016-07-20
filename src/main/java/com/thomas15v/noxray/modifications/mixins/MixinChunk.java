package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.api.NetworkBlockContainer;
import com.thomas15v.noxray.api.NetworkChunk;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import com.thomas15v.noxray.modifications.internal.InternalChunk;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Chunk.class)
public class MixinChunk implements InternalChunk {

    @Shadow
    public int xPosition;
    @Shadow
    public int zPosition;
    @Shadow
    private World worldObj;
    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    private NetworkChunk networkChunk;

    @Inject(method = "onChunkLoad", at = @At("RETURN"))
    public void setChunkData(CallbackInfo info){
        //support for multiworlds (forge or vanilla) I don't see a reason to obfuscateBlocks the end or the nether
        obFuscateChunk();
    }

    @Override
    public void obFuscateChunk(){
        final long startTime = System.currentTimeMillis();

        if (worldObj.getWorldType().getWorldTypeID() != -1 && worldObj.getWorldType().getWorldTypeID() != 1 && networkChunk == null) {
            NetworkBlockContainer[] blockContainers = new NetworkBlockContainer[storageArrays.length];
            for (int i = 0; i < storageArrays.length; i++) {
                if (storageArrays[i] != null){
                    blockContainers[i] = ((InternalBlockStateContainer) storageArrays[i].getData()).getBlockContainer();
                }

            }
            networkChunk = new NetworkChunk(blockContainers, (org.spongepowered.api.world.Chunk) this);
            ((InternalWorld)worldObj).getNetworkWorld().addChunk(networkChunk);

            networkChunk.obfuscateBlocks();
        }
        final long time = System.currentTimeMillis() - startTime;
        if (time > 5){
            System.out.println(time);
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
