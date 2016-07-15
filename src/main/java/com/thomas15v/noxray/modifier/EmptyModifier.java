package com.thomas15v.noxray.modifier;

import com.flowpowered.math.vector.Vector3i;
import com.thomas15v.noxray.api.BlockModifier;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;

import java.util.List;
import java.util.function.Predicate;

public class EmptyModifier implements BlockModifier {

    @Override
    public BlockState handleBlock(BlockState original, Vector3i location, List<BlockState> surroundingBlocks) {
        return null;
    }

    @Override
    public BlockState HandlePlayerSpecificBlock(BlockState original, Vector3i location, Player player) {
        return null;
    }

    @Override
    public Predicate<BlockState> getFilter() {
        return blockState -> false;
    }
}
