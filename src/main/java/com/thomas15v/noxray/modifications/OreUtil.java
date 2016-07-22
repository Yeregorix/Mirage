package com.thomas15v.noxray.modifications;

import net.minecraft.block.BlockOre;
import org.spongepowered.api.block.BlockType;

public class OreUtil {

    public static boolean isOre(BlockType blockState){
        return blockState instanceof BlockOre;
    }

}
