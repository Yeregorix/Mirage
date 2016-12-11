package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Represent the world viewed for the network (akka online players)
 */
public class NetworkWorld {

    // Thread-safe map
    private Map<Vector3i, NetworkChunk> networkChunkMap = new ConcurrentSkipListMap<>();
    //todo: modifiers for each world
    private BlockModifier modifier;

    public void addChunk(NetworkChunk chunk){
        networkChunkMap.put(chunk.getLocation(), chunk);
    }

    public void removeChunk(Vector3i pos){
        networkChunkMap.remove(pos);
    }

    @Nullable
    private NetworkChunk getChunk(Vector3i vector3i){
        return networkChunkMap.get(vector3i);
    }

    private Collection<NetworkChunk> getChunks(){
        return networkChunkMap.values();
    }
}
