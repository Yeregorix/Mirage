package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Represent the world viewed for the network (akka online players)
 */
public class NetworkWorld {

    private Map<Vector3i, NetworkChunk> networkChunkMap = new HashMap<>();
    //todo: modifiers for each world
    private BlockModifier modifier;

    public synchronized void addChunk(NetworkChunk chunk){
        Vector3i location = chunk.getLocation();
        networkChunkMap.put(location, chunk);
    }

    @Nullable
    private NetworkChunk getChunk(Vector3i vector3i){
        return networkChunkMap.get(vector3i);
    }

    private Collection<NetworkChunk> getChunks(){
        return networkChunkMap.values();
    }
}
