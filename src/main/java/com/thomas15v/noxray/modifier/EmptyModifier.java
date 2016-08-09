package com.thomas15v.noxray.modifier;

import com.thomas15v.noxray.api.BlockModifier;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;
import java.util.function.Predicate;

public class EmptyModifier implements BlockModifier {

    @Override
    public BlockState handleBlock(BlockState original, Location<World> location, List<BlockState> surroundingBlocks) {
        return null;
    }

    @Override
    public Predicate<BlockState> getFilter() {
        return blockState -> false;
    }
}
