package com.thomas15v.noxray.config;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;

import java.util.Arrays;
import java.util.List;

public class NoXrayConfig {

    private CommentedConfigurationNode node;
    private static final String ORE_BLOCK_KEY = "OreBlocks";
    private static final String ORE_BLOCK_COMMENT = "List of ore blocks that have to be obfuscated";
    private List<BlockType> oreBlocks = Arrays.asList(BlockTypes.DIAMOND_ORE, BlockTypes.COAL_ORE, BlockTypes.EMERALD_ORE, BlockTypes.GOLD_ORE, BlockTypes.LAPIS_ORE, BlockTypes.QUARTZ_ORE, BlockTypes.IRON_ORE);

    private static final String USE_ORE_DICT_KEY = "UseOreDict";
    private static final String USE_ORE_DICT_COMMENT = "In case forge is detected, use the forgedict to add extra ores";
    private boolean useOreDict = true;

    private boolean save = false;

    public NoXrayConfig(CommentedConfigurationNode node){
        this.node = node;
        //oreBlocks = handleNode(ORE_BLOCK_KEY, ORE_BLOCK_COMMENT, oreBlocks);
        //useOreDict = handleNode(USE_ORE_DICT_KEY, USE_ORE_DICT_COMMENT, useOreDict);
    }

    private <I> I handleNode(String key, String comment, I value){
        CommentedConfigurationNode Keynode = node.getNode(key);
        if (Keynode.getValue() == null){
            Keynode.setComment(comment);
            Keynode.setValue(value);
            save = true;
        }
        return (I) node.getValue();
    }

    /*public List<BlockType> getOreBlocks() {
        return oreBlocks;
    }*/

    public boolean isUseOreDict() {
        return useOreDict;
    }

    public boolean isSave() {
        return save;
    }
}
