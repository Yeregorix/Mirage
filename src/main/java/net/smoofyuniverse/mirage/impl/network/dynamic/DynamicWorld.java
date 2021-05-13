/*
 * Copyright (c) 2018-2021 Hugo Dupanloup (Yeregorix)
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

import com.flowpowered.math.vector.Vector3i;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import org.spongepowered.api.entity.living.player.Player;

import static net.smoofyuniverse.mirage.impl.network.NetworkChunk.asLong;

public final class DynamicWorld {
	private final NetworkWorld view;
	private final Player player;

	private final Long2ObjectMap<DynamicChunk> chunks = new Long2ObjectOpenHashMap<>();
	private Vector3i center;

	public DynamicWorld(NetworkWorld view, Player player) {
		this.view = view;
		this.player = player;
	}

	public void updateCenter() {
		setCenter(this.player.getPosition().add(0, 1.62, 0).toInt());
	}

	public Vector3i getCenter() {
		return this.center;
	}

	private void setCenter(Vector3i center) {
		if (center.equals(this.center))
			return;

		MirageTimings.DYNAMISM.startTiming();

		this.center = center;
		for (DynamicChunk chunk : this.chunks.values())
			chunk.updateCenter();

		MirageTimings.DYNAMISM.stopTiming();
	}

	public DynamicChunk createChunk(int x, int z) {
		DynamicChunk chunk = new DynamicChunk(this, this.view.getChunk(x, z));
		this.chunks.put(asLong(x, z), chunk);

		MirageTimings.DYNAMISM.startTiming();
		chunk.updateCenter();
		MirageTimings.DYNAMISM.stopTiming();

		return chunk;
	}

	public void removeChunk(int x, int z) {
		this.chunks.remove(asLong(x, z));
	}
}
