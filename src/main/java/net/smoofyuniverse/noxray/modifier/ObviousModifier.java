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

package net.smoofyuniverse.noxray.modifier;

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.noxray.NoXray;
import net.smoofyuniverse.noxray.api.AbstractModifier;
import net.smoofyuniverse.noxray.api.volume.ChunkView;
import net.smoofyuniverse.noxray.config.Options;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;

import java.util.Random;

/**
 * Only modifies the obviously ore blocks, And is the less lagiest solution
 */
public class ObviousModifier extends AbstractModifier {
	public static final ObviousModifier INSTANCE = new ObviousModifier();

	private ObviousModifier() {
		super(NoXray.get(), "Obvious");
	}

	@Override
	public boolean isReady(ChunkView view) {
		return view.isExpositionCheckReady();
	}

	@Override
	public void modify(ChunkView view, Random r) {
		Vector3i min = view.getBlockMin(), max = view.getBlockMax();
		Options options = view.getWorld().getOptions();

		for (int y = min.getY(); y <= max.getY(); y++) {
			for (int z = min.getZ(); z <= max.getZ(); z++) {
				for (int x = min.getX(); x <= max.getX(); x++) {
					BlockState b = view.getBlock(x, y, z);
					if (b == BlockTypes.AIR || b == options.ground)
						continue;

					if (options.oresSet.contains(b) && !view.isExposed(x, y, z))
						view.setBlock(x, y, z, options.ground);
				}
			}
		}
	}
}
