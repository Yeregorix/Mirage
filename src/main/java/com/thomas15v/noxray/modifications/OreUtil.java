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

	public static boolean isOre(BlockType blockState) {
		return blockState instanceof BlockOre | Ores.contains(blockState);
	}

	public static void addOre(BlockType blockType) {
		Ores.add(blockType);
	}

	public static void registerForgeOres() {
		for (String s : OreDictionary.getOreNames()) {
			if (s.contains("ore")) {
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
