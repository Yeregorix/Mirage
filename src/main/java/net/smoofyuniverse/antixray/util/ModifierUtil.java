/*
 * Copyright (c) 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.antixray.util;

import net.smoofyuniverse.antixray.util.collection.BlockSet;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;

public class ModifierUtil {

	public static BlockSet getCommonResources(DimensionType dimType) {
		BlockSet set = new BlockSet();
		if (dimType == DimensionTypes.NETHER) {
			set.add(BlockTypes.LAVA);
			set.add(BlockTypes.MAGMA);
			set.add(BlockTypes.GLOWSTONE);
			set.add(BlockTypes.QUARTZ_ORE);
		} else if (dimType == DimensionTypes.OVERWORLD) {
			set.add(BlockTypes.REDSTONE_ORE);
			set.add(BlockTypes.EMERALD_ORE);
			set.add(BlockTypes.DIAMOND_ORE);
			set.add(BlockTypes.COAL_ORE);
			set.add(BlockTypes.IRON_ORE);
			set.add(BlockTypes.LAPIS_ORE);
			set.add(BlockTypes.GOLD_ORE);
		}
		return set;
	}

	public static BlockState getCommonGround(DimensionType dimType) {
		if (dimType == DimensionTypes.NETHER)
			return BlockTypes.NETHERRACK.getDefaultState();
		if (dimType == DimensionTypes.THE_END)
			return BlockTypes.END_STONE.getDefaultState();
		return BlockTypes.STONE.getDefaultState();
	}
}
