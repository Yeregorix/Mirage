package com.thomas15v.noxray.modifier;

import com.thomas15v.noxray.modifications.NMSUtil;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public class PlayerGenerationModifier extends GenerationModifier {

    @Override
    public BlockState handlePlayerBlock(BlockState original, Location<World> location) {
        if (NMSUtil.getLightLevel(location) == 0 && !NMSUtil.canSeeTheSky(location)) {
            return BlockTypes.REDSTONE_BLOCK.getDefaultState();
        }else {
            return super.handlePlayerBlock(original, location);
        }
    }

}
