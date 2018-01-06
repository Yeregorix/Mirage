/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.thomas15v.noxray.modifications;

import net.minecraft.block.BlockOre;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.property.block.FullBlockSelectionBoxProperty;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;

import java.util.*;

public class OreUtil {
	private static final Set<BlockType> oresSet = new HashSet<>();
	private static final List<BlockType> oresList = new ArrayList<>();

	public static boolean isOre(BlockType type) {
		return type instanceof BlockOre | oresSet.contains(type);
	}

	public static BlockType getRandomOre(Random r) {
		return oresList.get(r.nextInt(oresList.size()));
	}

	public static void registerForgeOres() {
		for (String s : OreDictionary.getOreNames()) {
			if (s.contains("ore")) {
				for (ItemStack stack : OreDictionary.getOres(s)) {
					Item item = stack.getItem();
					if (item instanceof ItemBlock)
						addOre((BlockType) ((ItemBlock) item).getBlock());
				}
			}
		}
	}

	public static void addOre(BlockType type) {
		if (!(type instanceof BlockOre))
			oresSet.add(type);
		oresList.add(type);
	}

	public static boolean isExposed(Iterable<BlockType> it) {
		for (BlockType type : it) {
			if (!type.getProperty(FullBlockSelectionBoxProperty.class).get().getValue())
				return true;
		}
		return false;
	}

	public static BlockType getCommonGroundBlockType(DimensionType type) {
		if (type == DimensionTypes.OVERWORLD)
			return BlockTypes.STONE;
		if (type == DimensionTypes.NETHER)
			return BlockTypes.NETHERRACK;
		if (type == DimensionTypes.THE_END)
			return BlockTypes.END_STONE;
		return BlockTypes.STONE;
	}

	static {
		addOre(BlockTypes.REDSTONE_ORE);
		addOre(BlockTypes.MONSTER_EGG);
		addOre(BlockTypes.EMERALD_ORE);
		addOre(BlockTypes.DIAMOND_ORE);
		addOre(BlockTypes.COAL_ORE);
		addOre(BlockTypes.IRON_ORE);
		addOre(BlockTypes.LAPIS_ORE);
		addOre(BlockTypes.GOLD_ORE);
	}
}
