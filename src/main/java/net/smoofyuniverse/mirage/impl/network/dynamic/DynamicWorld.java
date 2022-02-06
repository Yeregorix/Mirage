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

package net.smoofyuniverse.mirage.impl.network.dynamic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.math.vector.Vector3i;

import javax.annotation.Nullable;

import static net.smoofyuniverse.mirage.impl.network.NetworkChunk.asLong;

public final class DynamicWorld {
	private final InternalWorld storage;
	private final Player player;

	private final Long2ObjectMap<DynamicChunk> chunks = new Long2ObjectOpenHashMap<>();
	private Vector3i center;

	public DynamicWorld(InternalWorld storage, Player player) {
		this.storage = storage;
		this.player = player;
	}

	public void updateCenter() {
		Vector3i newCenter = this.player.position().add(0, 1.62, 0).toInt();
		if (!newCenter.equals(this.center)) {
			this.center = newCenter;
			for (DynamicChunk chunk : this.chunks.values())
				chunk.updateCenter();
		}
	}

	public Vector3i getCenter() {
		return this.center;
	}

	public DynamicChunk getOrCreateChunk(int x, int z) {
		long pos = asLong(x, z);
		DynamicChunk chunk = this.chunks.get(pos);
		if (chunk == null) {
			chunk = new DynamicChunk(this, this.storage.opaqueChunk(x, z));
			this.chunks.put(asLong(x, z), chunk);
			chunk.updateCenter();
		}
		return chunk;
	}

	@Nullable
	public DynamicChunk getChunk(int x, int z) {
		return this.chunks.get(asLong(x, z));
	}

	public void removeChunk(int x, int z) {
		this.chunks.remove(asLong(x, z));
	}
}
