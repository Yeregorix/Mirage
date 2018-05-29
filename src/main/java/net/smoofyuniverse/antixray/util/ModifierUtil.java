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

import net.minecraft.block.state.IBlockState;
import net.smoofyuniverse.antixray.util.collection.BlockSet;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;

public class ModifierUtil {

	public static boolean isOpaque(BlockState state) {
		return ((IBlockState) state).isOpaqueCube();
	}

	public static void getCommonResources(BlockSet set, DimensionType dimType) {
		if (dimType == DimensionTypes.NETHER) {
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
	}

	public static void getRareResources(BlockSet set, DimensionType dimType) {
		set.add(BlockTypes.MOB_SPAWNER);
		set.add(BlockTypes.CHEST);
		set.add(BlockTypes.TRAPPED_CHEST);

		if (dimType == DimensionTypes.NETHER) {
			set.add(BlockTypes.PORTAL);
		} else if (dimType == DimensionTypes.OVERWORLD) {
			set.add(BlockTypes.MOSSY_COBBLESTONE);
			set.add(BlockTypes.MONSTER_EGG);

			set.add(BlockTypes.PORTAL);

			set.add(BlockTypes.END_PORTAL);
			set.add(BlockTypes.END_PORTAL_FRAME);
		} else if (dimType == DimensionTypes.THE_END) {
			set.add(BlockTypes.END_PORTAL);
			set.add(BlockTypes.END_PORTAL_FRAME);
		}
	}

	public static void getWaterResources(BlockSet set, DimensionType dimType) {
		if (dimType == DimensionTypes.OVERWORLD) {
			set.add(BlockTypes.SEA_LANTERN);
			set.add(BlockTypes.PRISMARINE);
			set.add(BlockTypes.GOLD_BLOCK);
		}
	}

	public static BlockState getCommonGround(DimensionType dimType) {
		if (dimType == DimensionTypes.NETHER)
			return BlockTypes.NETHERRACK.getDefaultState();
		if (dimType == DimensionTypes.THE_END)
			return BlockTypes.END_STONE.getDefaultState();
		return BlockTypes.STONE.getDefaultState();
	}
}
