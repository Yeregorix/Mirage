/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.impl.network.dynamism;

import com.flowpowered.math.vector.Vector3i;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.util.Tuple;

import java.util.Optional;
import java.util.UUID;

public final class PlayerDynamismManager {
	public final UUID playerId;
	private Long2ObjectMap<Tuple<DynamicChunk, ChunkChangeListener>> chunks = new Long2ObjectOpenHashMap<>();
	private Vector3i center;

	public PlayerDynamismManager(UUID playerId) {
		this.playerId = playerId;
	}

	public void update(Player player) {
		if (!player.getUniqueId().equals(this.playerId))
			throw new IllegalArgumentException("Player");

		Vector3i pos = player.getPosition().toInt();
		if (this.center == null || !this.center.equals(pos))
			setCenter(pos);
	}

	private void setCenter(DynamicChunk dynChunk, ChunkChangeListener listener) {
		dynChunk.setCenter(this.center);
		dynChunk.clear();

		InternalChunk chunk = listener.getChunk();
		if (chunk != null) {
			chunk.getView().collectDynamicPositions(dynChunk);
			listener.markDirty();
		}
	}

	public Optional<Vector3i> getCenter() {
		return Optional.ofNullable(this.center);
	}

	public void setCenter(Vector3i center) {
		MirageTimings.DYNAMISM.startTiming();

		this.center = center;
		for (Tuple<DynamicChunk, ChunkChangeListener> t : this.chunks.values())
			setCenter(t.getFirst(), t.getSecond());

		MirageTimings.DYNAMISM.stopTiming();
	}

	public void addChunk(DynamicChunk dynChunk, ChunkChangeListener listener) {
		if (dynChunk == null || listener == null)
			throw new IllegalArgumentException();

		this.chunks.put(index(dynChunk.x, dynChunk.z), new Tuple<>(dynChunk, listener));

		if (this.center != null) {
			MirageTimings.DYNAMISM.startTiming();
			setCenter(dynChunk, listener);
			MirageTimings.DYNAMISM.stopTiming();
		}
	}

	private static long index(int x, int z) {
		return (long) x + 2147483647L | (long) z + 2147483647L << 32;
	}

	public void removeChunk(int x, int z) {
		this.chunks.remove(index(x, z));
	}
}
