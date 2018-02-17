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
import org.spongepowered.api.util.Direction;

import java.util.Optional;

/**
 * Represents an immutable server-side chunk.
 */
public interface ChunkStorage extends BlockStorage {

	/**
	 * @return The ChunkView which is associated with this ChunkStorage
	 * @throws IllegalStateException if the ChunkView is not available
	 * @see ChunkStorage#isViewAvailable
	 */
	@Override
	ChunkView getView();

	/**
	 * @return Whether the ChunkView associated with this ChunkStorage is available
	 */
	boolean isViewAvailable();

	/**
	 * @param dir The direction
	 * @return Whether neighbor chunk according to the given direction is loaded
	 */
	default boolean isNeighborLoaded(Direction dir) {
		return getNeighborStorage(dir).isPresent();
	}

	/**
	 * @param dir The direction
	 * @return The neighbor chunk according to the given direction
	 */
	default Optional<ChunkStorage> getNeighborStorage(Direction dir) {
		if (dir.isSecondaryOrdinal())
			throw new IllegalArgumentException("Direction");
		return getWorld().getChunkStorage(getPosition().add(dir.asBlockOffset()));
	}

	/**
	 * Gets the world the chunk is in.
	 *
	 * @return The world
	 */
	WorldStorage getWorld();

	/**
	 * Gets the position of the chunk.
	 *
	 * @return The position
	 */
	Vector3i getPosition();

	/**
	 * Checks if neighbor chunks are loaded to be sure that we can call {@link BlockStorage#isExposed(int, int, int)}.
	 *
	 * @return Whether neighbor chunks are loaded
	 */
	boolean areNeighborsLoaded();
}
