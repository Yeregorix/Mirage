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
import org.spongepowered.api.util.Identifiable;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Optional;

/**
 * Represents an immutable server-side world.
 */
public interface WorldStorage extends BlockStorage, Identifiable {

	/**
	 * @return The WorldView which is associated with this WorldStorage
	 */
	@Override
	WorldView getView();

	/**
	 * @return The name of this world
	 */
	String getName();

	/**
	 * @return The properties of this world
	 */
	WorldProperties getProperties();

	default boolean isChunkLoaded(Vector3i pos) {
		return isChunkLoaded(pos.getX(), pos.getY(), pos.getZ());
	}

	default boolean isChunkLoaded(int x, int y, int z) {
		return getChunkStorage(x, y, z).isPresent();
	}

	Optional<ChunkStorage> getChunkStorage(int x, int y, int z);

	default Optional<ChunkStorage> getChunkStorage(Vector3i pos) {
		return getChunkStorage(pos.getX(), pos.getY(), pos.getZ());
	}

	default Optional<ChunkStorage> getChunkStorageAt(Vector3i pos) {
		return getChunkStorage(pos.getX(), pos.getY(), pos.getZ());
	}

	Optional<ChunkStorage> getChunkStorageAt(int x, int y, int z);
}
