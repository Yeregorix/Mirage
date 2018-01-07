/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

import org.spongepowered.api.block.BlockState;

import javax.annotation.Nullable;
import java.util.Random;

public interface BlockModifier {
	BlockModifier EMPTY = (storage, r, x, y, z) -> null;

	/**
	 * This method gets called when a block gets read from the disk.
	 * Not that this function should not trigger another chunkload operation since this could cause a stack overflow.
	 * THIS METHOD IS ASYNC AND WE CAN'T CALL SPONGE API FUNCTIONS FROM HERE
	 *
	 * @return a BlockState if the block has been modified, null else.
	 */
	@Nullable
	BlockState modify(BlockStorage storage, Random r, int x, int y, int z);
}
