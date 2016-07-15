package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.Chunk;

/**
 * Represent a chunk viewed for the network (akka online players)
 */
public class NetworkChunk {

    private NetworkBlockContainer[] blockStateContainers;
    private Chunk chunk;

    public NetworkChunk(NetworkBlockContainer[] blockStateContainers, Chunk chunk){
        this.blockStateContainers = blockStateContainers;
        this.chunk = chunk;
    }

    public BlockState get(Vector3i vector3i){
        return get(vector3i.getX(), vector3i.getY(), vector3i.getZ());
    }

    public BlockState get(int x, int y, int z){
        return (BlockState) getBlockContainerFor(y).get(y & 15, y & 15, z & 15);
    }

    private NetworkBlockContainer getBlockContainerFor(int y){
        return blockStateContainers[y >> 4];
    }

    public void set(Vector3i vector3i, BlockState blockState){
        set(vector3i.getX(), vector3i.getY(), vector3i.getZ(), blockState);
    }

    public void set(int x, int y, int z, BlockState blockState){
        getBlockContainerFor(y).set(x,y,z, (IBlockState) blockState);
    }

    public void obfuscateInnerBlocks(){
        for (NetworkBlockContainer blockStateContainer : blockStateContainers) {
            if (blockStateContainer != null){
                blockStateContainer.obfuscateInnerBlocks(this);
            }
        }
    }

    public void ObfuscateOutSideBlocks(NetworkChunk... networkChunks){
        for (NetworkBlockContainer blockStateContainer : blockStateContainers) {
            if (blockStateContainer != null){
                blockStateContainer.ObfuscateOutSideBlocks(this, networkChunks);
            }
        }
        //chunk.addScheduledUpdate();
    }

    public Vector3i getLocation(){
        return chunk.getPosition();
    }
}
