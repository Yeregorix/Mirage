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

package net.smoofyuniverse.antixray.api.volume;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.util.Direction;

import java.util.Optional;

/**
 * Represents a mutable client-side chunk.
 */
public interface ChunkView extends BlockView {

	/**
	 * @return The ChunkStorage which is associated with this ChunkView
	 */
	@Override
	ChunkStorage getStorage();

	/**
	 * @param dir The direction
	 * @return Whether neighbor chunk according to the given direction is loaded
	 */
	default boolean isNeighborLoaded(Direction dir) {
		return getNeighborView(dir).isPresent();
	}

	/**
	 * @param dir The direction
	 * @return The neighbor chunk according to the given direction
	 */
	default Optional<ChunkView> getNeighborView(Direction dir) {
		if (dir.isSecondaryOrdinal())
			throw new IllegalArgumentException("Direction");
		return getWorld().getChunkView(getPosition().add(dir.asBlockOffset()));
	}

	/**
	 * Gets the world the chunk is in.
	 *
	 * @return The world
	 */
	WorldView getWorld();

	/**
	 * Gets the position of the chunk.
	 *
	 * @return The position
	 */
	Vector3i getPosition();

	/**
	 * Checks if neighbor chunks are loaded to be sure that we can call {@link BlockView#isExposed(int, int, int)}.
	 *
	 * @return Whether neighbor chunks are loaded
	 */
	boolean areNeighborsLoaded();

	/**
	 * @return Whether this chunk is marked as obfuscated.
	 */
	boolean isObfuscated();

	/**
	 * If not already done and if all modifiers are ready, this method obfuscates all blocks inside this chunk.
	 */
	void obfuscate();

	/**
	 * If the chunk marked as obfuscated, this method deofuscates all blocks inside this chunk.
	 */
	void deobfuscate();

	/**
	 * Deobfuscates a single block at the given position.
	 *
	 * @param pos The position
	 * @return Whether the block was different before being deobfuscated
	 */
	default boolean deobfuscate(Vector3i pos) {
		return deobfuscate(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Deobfuscates a single block at the given position.
	 *
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the block was different before being deobfuscated
	 */
	boolean deobfuscate(int x, int y, int z);

	/**
	 * Resets the dynamism distance of all positions
	 */
	void clearDynamism();
}
