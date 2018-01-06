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

package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.extent.BlockVolume;

import java.util.ArrayList;
import java.util.List;

public interface BlockStorage extends BlockVolume {

	default List<BlockType> getSurroundingBlockTypes(Vector3i pos) {
		return getSurroundingBlockTypes(pos.getX(), pos.getY(), pos.getZ());
	}

	default List<BlockType> getSurroundingBlockTypes(int x, int y, int z) {
		List<BlockType> list = new ArrayList<>();

		if (y != 256)
			list.add(getBlockType(x, y + 1, z));
		if (y != 0)
			list.add(getBlockType(x, y - 1, z));

		list.add(getBlockType(x + 1, y, z));
		list.add(getBlockType(x, y, z + 1));
		list.add(getBlockType(x - 1, y, z));
		list.add(getBlockType(x, y, z - 1));

		return list;
	}

	default List<BlockState> getSurroundingBlocks(Vector3i pos) {
		return getSurroundingBlocks(pos.getX(), pos.getY(), pos.getZ());
	}

	default List<BlockState> getSurroundingBlocks(int x, int y, int z) {
		List<BlockState> list = new ArrayList<>();

		if (y != 256)
			list.add(getBlock(x, y + 1, z));
		if (y != 0)
			list.add(getBlock(x, y - 1, z));

		list.add(getBlock(x + 1, y, z));
		list.add(getBlock(x, y, z + 1));
		list.add(getBlock(x - 1, y, z));
		list.add(getBlock(x, y, z - 1));

		return list;
	}

	default int getBlockLightLevel(Vector3i pos) {
		return getBlockLightLevel(pos.getX(), pos.getY(), pos.getZ());
	}

	int getBlockLightLevel(int x, int y, int z);

	default boolean canSeeTheSky(Vector3i pos) {
		return canSeeTheSky(pos.getX(), pos.getY(), pos.getZ());
	}

	boolean canSeeTheSky(int x, int y, int z);

	DimensionType getDimensionType();

	default BlockState getCommonGroundBlock() {
		return getCommonGroundBlockType().getDefaultState();
	}

	BlockType getCommonGroundBlockType();
}
