/*
 * The MIT License (MIT)
 *
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

package net.smoofyuniverse.antixray.api.util;

import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;

import java.util.ArrayList;
import java.util.List;

public class ModifierUtil {

	public static List<BlockState> getCommonOres(DimensionType dimType) {
		List<BlockState> ores = new ArrayList<>();
		if (dimType == DimensionTypes.NETHER) {
			ores.add(BlockTypes.QUARTZ_ORE.getDefaultState());
		} else if (dimType == DimensionTypes.OVERWORLD) {
			ores.add(BlockTypes.REDSTONE_ORE.getDefaultState());
			ores.add(BlockTypes.EMERALD_ORE.getDefaultState());
			ores.add(BlockTypes.DIAMOND_ORE.getDefaultState());
			ores.add(BlockTypes.COAL_ORE.getDefaultState());
			ores.add(BlockTypes.IRON_ORE.getDefaultState());
			ores.add(BlockTypes.LAPIS_ORE.getDefaultState());
			ores.add(BlockTypes.GOLD_ORE.getDefaultState());
		}
		return ores;
	}

	public static BlockState getCommonGround(DimensionType dimType) {
		if (dimType == DimensionTypes.NETHER)
			return BlockTypes.NETHERRACK.getDefaultState();
		if (dimType == DimensionTypes.THE_END)
			return BlockTypes.END_STONE.getDefaultState();
		return BlockTypes.STONE.getDefaultState();
	}
}
