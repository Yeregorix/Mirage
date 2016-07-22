package com.thomas15v.noxray.modifications;

import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;

import java.util.HashSet;
import java.util.Set;

public class OreUtil {

    private static Set<BlockType> Ores = new HashSet<>();

    static {
        addOre(BlockTypes.REDSTONE_ORE);
        addOre(BlockTypes.MONSTER_EGG);
        //other ores are covered by BlockOre class
    }

    public static boolean isOre(BlockType blockState){
        return blockState instanceof BlockOre | Ores.contains(blockState);
    }

    public static void addOre(BlockType blockType){
        Ores.add(blockType);
    }

    public static void registerForgeOres(){
        for (String s : OreDictionary.getOreNames()) {
            if (s.contains("ore")){
                OreDictionary.getOres(s).stream().filter(ore -> ore.getItem() instanceof ItemBlock).forEach(ore -> {
                    Block block = ((ItemBlock) ore.getItem()).getBlock();
                    if (!(block instanceof BlockOre)) {
                        OreUtil.addOre((BlockType) block);
                    }
                });
            }
        }
    }
}
