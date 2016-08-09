package com.thomas15v.noxray.modifications;

import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 * A class that contains stuff that Sponge prob should take a look at :).
 */
public class NMSUtil {

    public static int getLightLevel(Location<World> location){
        return ((net.minecraft.world.World)location.getExtent()).
                getLight(getBlockPos(location));
    }

    public static boolean canSeeTheSky(Location<World> location){
        return ((net.minecraft.world.World)location.getExtent()).canSeeSky(getBlockPos(location));
    }

    private static BlockPos getBlockPos(Location<World> location){
        return new BlockPos(location.getX(), location.getY(), location.getZ());
    }

}
