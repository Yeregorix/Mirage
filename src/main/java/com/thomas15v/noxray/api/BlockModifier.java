package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;
import java.util.function.Predicate;

public interface BlockModifier {

    /**
     * This method gets called when a block gets read from the disk.
     * Not that this function should not trigger another chunkload operation since this could cause a stack overflow.
     * THIS METHOD IS ASYNC AND NOT THREADSAFE AND WE CANT CALL SPONGE API FUNCTIONS FROM HERE
     *
     * @param original The original block
     * @param location The location of the block
     * @param surroundingBlocks list with surrounding blocks
     * @return a ReadBlockResponse with the modifiedblock and if the block should be handled for players.
     */
    BlockState handleBlock(BlockState original, Vector3i location, List<BlockState> surroundingBlocks);

    /**
     * Handle blocks for a specified player.
     *
     * @param original The original block
     * @param location The location of the block
     * @param player The player the data will be sended to
     * @return The block that will be replaced. return the original for no change
     */
    BlockState HandlePlayerSpecificBlock(BlockState original, Vector3i location, Player player);

    Predicate<BlockState> getFilter();
}
