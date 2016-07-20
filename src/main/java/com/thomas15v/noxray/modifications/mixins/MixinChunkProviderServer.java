package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.modifications.internal.InternalMixinServerProvider;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Deprecated
@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer implements InternalMixinServerProvider {

    @Shadow
    private Long2ObjectMap<Chunk> id2ChunkMap;

    @Override
    public Long2ObjectMap<Chunk> getId2ChunkMap() {
        return id2ChunkMap;
    }
}
