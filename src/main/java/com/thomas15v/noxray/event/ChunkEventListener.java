package com.thomas15v.noxray.event;

import com.thomas15v.noxray.api.NetworkWorld;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.world.chunk.UnloadChunkEvent;

public class ChunkEventListener {

	@Listener
	public void onChunkUnload(UnloadChunkEvent event) {
		NetworkWorld networkWorld = ((InternalWorld) event.getTargetChunk().getWorld()).getNetworkWorld();
		networkWorld.removeChunk(event.getTargetChunk().getPosition());
	}
}
