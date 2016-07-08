package com.thomas15v.noxray.modifications.internal;

import net.minecraft.network.PacketBuffer;
import net.minecraft.world.chunk.Chunk;

public interface InternalBlockStateContainer {

    int modifiedSize();

    void writeModified(PacketBuffer buffer);

    void updateModified(Chunk chunk);

    void setY(int y);

    int getY();
}
