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

package com.thomas15v.noxray.modifier;

import com.thomas15v.noxray.api.BlockModifier;
import com.thomas15v.noxray.api.BlockStorage;
import com.thomas15v.noxray.config.Options;
import org.spongepowered.api.block.BlockState;

import javax.annotation.Nullable;
import java.util.Random;

public class RandomModifier implements BlockModifier {

	@Nullable
	@Override
	public BlockState modify(BlockStorage storage, Options options, Random r, int x, int y, int z) {
		BlockState block = storage.getBlock(x, y, z);
		if (block != options.ground && !options.oresSet.contains(block))
			return null;

		if (ModifierUtil.isExposed(storage.getSurroundingBlockTypes(x, y, z)))
			return null;

		if (options.ores == 0)
			return options.ground;

		if (options.density != 1 && r.nextFloat() > options.density)
			return options.ground;

		return options.oresList.get(r.nextInt(options.ores));
	}
}
