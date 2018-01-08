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

package net.smoofyuniverse.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.world.extent.BlockVolume;

public interface BlockStorage extends BlockVolume {

	default boolean isExposed(Vector3i pos) {
		return isExposed(pos.getX(), pos.getY(), pos.getZ());
	}

	boolean isExposed(int x, int y, int z);

	default int getBlockLightLevel(Vector3i pos) {
		return getBlockLightLevel(pos.getX(), pos.getY(), pos.getZ());
	}

	int getBlockLightLevel(int x, int y, int z);

	default boolean canSeeTheSky(Vector3i pos) {
		return canSeeTheSky(pos.getX(), pos.getY(), pos.getZ());
	}

	boolean canSeeTheSky(int x, int y, int z);
}
