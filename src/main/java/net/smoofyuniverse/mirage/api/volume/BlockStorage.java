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

package net.smoofyuniverse.mirage.api.volume;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;

/**
 * A BlockStorage is an ImmutableBlockVolume associated with mutable BlockView. This object is used to represent a server-side BlockVolume.
 */
public interface BlockStorage extends ImmutableBlockVolume {

	/**
	 * @return The BlockView which is associated with this BlockStorage
	 */
	BlockView getView();

	/**
	 * @param minX The X minimum position
	 * @param minY The Y minimum position
	 * @param minZ The Z minimum position
	 * @param maxX The X maximum position
	 * @param maxY The Y maximum position
	 * @param maxZ The Z maximum position
	 * @throws PositionOutOfBoundsException if one of the two positions is not contained in the volume
	 * @throws IllegalArgumentException     if the two positions does not defines a valid area
	 */
	default void checkBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		checkBlockPosition(minX, minY, minZ);
		checkBlockPosition(maxX, maxY, maxZ);

		if (minX > maxX || minY > maxY || minZ > maxZ)
			throw new IllegalArgumentException("Invalid area");
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @throws PositionOutOfBoundsException if the position is not contained in the volume
	 */
	default void checkBlockPosition(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			throw new PositionOutOfBoundsException(new Vector3i(x, y, z), getBlockMin(), getBlockMax());
	}

	/**
	 * Checks if the block at the given position is exposed to the view of normal users.
	 *
	 * @param pos The position
	 * @return Whether the block is exposed
	 */
	default boolean isExposed(Vector3i pos) {
		return isExposed(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Checks if the block at the given position is exposed to the view of normal users.
	 *
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the block is exposed
	 */
	boolean isExposed(int x, int y, int z);

	/**
	 * @param pos The position
	 * @return The block light level at the given position
	 */
	default int getBlockLightLevel(Vector3i pos) {
		return getBlockLightLevel(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return The block light level at the given position
	 */
	int getBlockLightLevel(int x, int y, int z);

	/**
	 * @param pos The position
	 * @return Whether the block at the given position is directly exposed to the sky
	 */
	default boolean canSeeTheSky(Vector3i pos) {
		return canSeeTheSky(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the block at the given position is directly exposed to the sky
	 */
	boolean canSeeTheSky(int x, int y, int z);
}
