/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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

import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.util.Identifiable;
import org.spongepowered.api.world.server.storage.ServerWorldProperties;
import org.spongepowered.math.vector.Vector3i;

import java.util.Optional;
import java.util.stream.Stream;

public interface OpaqueWorld<O extends OpaqueChunk> extends OpaqueBlockVolume, Identifiable {

	/**
	 * @return The key
	 * @see ServerWorldProperties#key()
	 */
	ResourceKey key();

	/**
	 * @return The properties of this world
	 */
	ServerWorldProperties properties();

	/**
	 * @param pos The position
	 * @return Whether the chunk at the given <strong>chunk</strong> position is loaded
	 */
	default boolean isOpaqueChunkLoaded(Vector3i pos) {
		return isOpaqueChunkLoaded(pos.x(), pos.y(), pos.z());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the chunk at the given <strong>chunk</strong> position is loaded
	 */
	boolean isOpaqueChunkLoaded(int x, int y, int z);

	/**
	 * @param pos The position
	 * @return The chunk at the given <strong>chunk</strong> position
	 */
	default Optional<O> opaqueChunk(Vector3i pos) {
		return opaqueChunk(pos.x(), pos.y(), pos.z());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return The chunk at the given <strong>chunk</strong> position
	 */
	Optional<O> opaqueChunk(int x, int y, int z);

	/**
	 * @param pos The position
	 * @return The chunk at the given <strong>block</strong> position
	 */
	default Optional<O> opaqueChunkAt(Vector3i pos) {
		return opaqueChunkAt(pos.x(), pos.y(), pos.z());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return The chunk at the given <strong>block</strong> position
	 */
	Optional<O> opaqueChunkAt(int x, int y, int z);

	/**
	 * @return A collection of all loaded chunks in this world
	 */
	Stream<? extends O> loadedOpaqueChunks();
}
