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
import net.smoofyuniverse.antixray.api.ViewModifier;
import net.smoofyuniverse.antixray.config.Options;

import java.util.Optional;

public interface WorldView extends BlockView {

	@Override
	WorldStorage getStorage();

	ViewModifier getModifier();

	Options getOptions();

	default boolean isChunkLoaded(Vector3i pos) {
		return isChunkLoaded(pos.getX(), pos.getY(), pos.getZ());
	}

	default boolean isChunkLoaded(int x, int y, int z) {
		return getChunkView(x, y, z).isPresent();
	}

	Optional<ChunkView> getChunkView(int x, int y, int z);

	default Optional<ChunkView> getChunkView(Vector3i pos) {
		return getChunkView(pos.getX(), pos.getY(), pos.getZ());
	}

	default Optional<ChunkView> getChunkViewAt(Vector3i pos) {
		return getChunkView(pos.getX(), pos.getY(), pos.getZ());
	}

	Optional<ChunkView> getChunkViewAt(int x, int y, int z);

	default boolean deobfuscate(Vector3i pos) {
		return deobfuscate(pos.getX(), pos.getY(), pos.getZ());
	}

	boolean deobfuscate(int x, int y, int z);
}
