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

    @Override
    public void obFuscate(){
        if (networkChunk == null) {
            if (worldObj.getWorldType().getWorldTypeID() != -1 && worldObj.getWorldType().getWorldTypeID() != 1 && networkChunk == null) {
                NetworkBlockContainer[] blockContainers = new NetworkBlockContainer[storageArrays.length];
                for (int i = 0; i < storageArrays.length; i++) {
                    if (storageArrays[i] != null) {
                        blockContainers[i] = ((InternalBlockStateContainer) storageArrays[i].getData()).getBlockContainer();
                    }
                }
                networkChunk = new NetworkChunk(blockContainers, (org.spongepowered.api.world.Chunk) this);
                ((InternalWorld) worldObj).getNetworkWorld().addChunk(networkChunk);
                networkChunk.obfuscate();
            }
        }
    }
}
