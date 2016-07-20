package com.thomas15v.noxray.modifier;

import com.flowpowered.math.vector.Vector3i;
import com.thomas15v.noxray.api.BlockModifier;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.property.block.HardnessProperty;
import org.spongepowered.api.data.property.block.MatterProperty;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Only modifies the blocks on disk read and so async. Causing almost no lag, but might increase length on the chunkloading (not that that is a problem really)
 */
public class GenerationModifier implements BlockModifier {

    private static final List<BlockType> COMMON_BLOCKS = Arrays.asList(BlockTypes.AIR, BlockTypes.STONE, BlockTypes.NETHERRACK, BlockTypes.END_STONE, BlockTypes.BEDROCK);
    private static final Predicate<BlockState> FILTER = blockState -> {
        if (COMMON_BLOCKS.contains(blockState.getType()) || blockState.getType().getProperty(MatterProperty.class).get().getValue() == MatterProperty.Matter.LIQUID) {
            return false;
        }
        Optional<HardnessProperty> hardnessProperty = blockState.getProperty(HardnessProperty.class);
        if (!hardnessProperty.isPresent()) {
            return false;
        } else if (hardnessProperty.get().getValue() < 3.0F) {
            return false;
        }
        return true;
    };

    @Override
    public BlockState handleBlock(BlockState original, Vector3i location, List<BlockState> surroundingBlocks) {
        for (BlockState surroundingBlock : surroundingBlocks) {
            if (original.getType() == BlockTypes.DIAMOND_BLOCK){
                System.out.println(surroundingBlocks + " " + location);
            }
            if (surroundingBlock.getType().equals(BlockTypes.AIR)) {
                return original;
            }
        }
        return BlockTypes.REDSTONE_BLOCK.getDefaultState();

    }

    @Override
    public BlockState HandlePlayerSpecificBlock(BlockState original, Vector3i location, Player player) {
        return null;
    }

    @Override
    public Predicate<BlockState> getFilter() {
        return FILTER;
    }


    public static boolean checkBlock(Location blockState, Direction direction) {
        return !blockState.getBlockRelative(direction).getBlock().getType().equals(BlockTypes.AIR);
    }
}
