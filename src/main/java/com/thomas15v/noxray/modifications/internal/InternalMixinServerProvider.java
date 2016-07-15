package com.thomas15v.noxray.modifications.internal;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.chunk.Chunk;

public interface InternalMixinServerProvider {
    Long2ObjectMap<Chunk> getId2ChunkMap();
}
