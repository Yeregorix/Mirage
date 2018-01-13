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

package net.smoofyuniverse.antixray.api.volume;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.world.extent.MutableBlockVolume;

/**
 * A BlockView is a MutableBlockVolume associated with immutable BlockStorage. This object is used to represent a client-side BlockVolume.
 */
public interface BlockView extends MutableBlockVolume {

	/**
	 * @return The BlockStorage which is associated with this BlockView
	 */
	BlockStorage getStorage();

	/**
	 * Checks if the block at the given position is exposed to the view of normal users.
	 * This can be done by checking the FullBlockSelectionBoxProperty of each surrounding block but this method is optimized for performances.
	 *
	 * @param pos The position
	 * @return Whether the block is exposed
	 */
	default boolean isExposed(Vector3i pos) {
		return isExposed(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Checks if the block at the given position is exposed to the view of normal users.
	 * This can be done by checking the FullBlockSelectionBoxProperty of each surrounding block but this method is optimized for performances.
	 *
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the block is exposed
	 */
	boolean isExposed(int x, int y, int z);
}
