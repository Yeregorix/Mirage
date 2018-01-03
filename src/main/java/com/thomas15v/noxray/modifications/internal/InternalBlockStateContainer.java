package com.thomas15v.noxray.modifications.internal;

import com.thomas15v.noxray.api.NetworkBlockContainer;
import net.minecraft.network.PacketBuffer;

public interface InternalBlockStateContainer {

	int modifiedSize();

	void writeModified(PacketBuffer buffer);

	void setY(int y);

	NetworkBlockContainer getBlockContainer();
}
