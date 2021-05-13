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
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.change.BlockChange;

import static net.smoofyuniverse.mirage.util.MathUtil.squared;

public final class DynamicChunk {
	public final DynamicWorld world;
	public final NetworkChunk view;

	private final ShortSet nextPositions = new ShortOpenHashSet();
	private final ShortSet currentPositions = new ShortOpenHashSet();
	private boolean modified;

	private Vector3i relativeCenter;

	DynamicChunk(DynamicWorld world, NetworkChunk view) {
		this.world = world;
		this.view = view;
	}

	public Vector3i getRelativeCenter() {
		return this.relativeCenter;
	}

	public void updateCenter() {
		setCenter(this.world.getCenter());
		clear();
		this.view.collectDynamicPositions(this);
	}

	private void setCenter(Vector3i center) {
		this.relativeCenter = center.sub(this.view.getBlockMin());
	}

	public void add(int x, int y, int z, int distance) {
		if (test(x, y, z, distance))
			add(x, y, z);
	}

	public boolean test(int x, int y, int z, int distance) {
		return this.relativeCenter.distanceSquared(x, y, z) <= squared(distance) << 8;
	}

	public void update(int x, int y, int z, int distance) {
		if (test(x, y, z, distance))
			add(x, y, z);
		else
			remove(x, y, z);
	}

	public void add(int x, int y, int z) {
		add(index(x, y, z));
	}

	public void remove(int x, int y, int z) {
		remove(index(x, y, z));
	}

	public void add(short pos) {
		if (this.nextPositions.add(pos))
			this.modified = true;
	}

	private static short index(int x, int y, int z) {
		return (short) ((x & 15) << 12 | (z & 15) << 8 | (y & 255));
	}

	public void remove(short pos) {
		if (this.nextPositions.rem(pos))
			this.modified = true;
	}

	public void clear() {
		if (!this.nextPositions.isEmpty()) {
			this.nextPositions.clear();
			this.modified = true;
		}
	}

	public boolean currentlyContains(int x, int y, int z) {
		return currentlyContains(index(x, y, z));
	}

	public boolean currentlyContains(short pos) {
		return this.currentPositions.contains(pos);
	}

	public BlockChange getChanges(boolean tileEntities) {
		BlockChange.Builder b = BlockChange.builder(this.view.getStorage());
		getChanges(b);
		return b.build(tileEntities);
	}

	public void getChanges(BlockChange.Builder b) {
		if (this.modified) {
			int minX = this.view.x << 4, minZ = this.view.z << 4;
			InternalChunk chunk = this.view.getStorage();

			// Reveal
			ShortIterator it = this.nextPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.currentPositions.contains(pos))
					b.add(pos, chunk.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
			}

			// Hide
			it = this.currentPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.nextPositions.contains(pos))
					b.add(pos, this.view.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
			}
		}
	}

	public BlockChange getCurrent(boolean tileEntities) {
		return getCurrent(65535, tileEntities);
	}

	public BlockChange getCurrent(int sections, boolean tileEntities) {
		BlockChange.Builder b = BlockChange.builder(this.view.getStorage());
		getCurrent(sections, b);
		return b.build(tileEntities);
	}

	public void getCurrent(int sections, BlockChange.Builder b) {
		if (sections == 0)
			return;

		int minX = this.view.x << 4, minZ = this.view.z << 4;
		InternalChunk chunk = this.view.getStorage();

		// Reveal
		ShortIterator it = this.currentPositions.iterator();
		if (sections == 65535) {
			while (it.hasNext()) {
				short pos = it.nextShort();
				b.add(pos, chunk.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
			}
		} else {
			while (it.hasNext()) {
				short pos = it.nextShort();
				int y = pos & 255;
				if ((sections & 1 << (y >> 4)) != 0)
					b.add(pos, chunk.getBlock(minX + (pos >> 12 & 15), y, minZ + (pos >> 8 & 15)));
			}
		}
	}

	public void getCurrent(BlockChange.Builder b) {
		getCurrent(65535, b);
	}

	public boolean hasChanges() {
		return this.modified;
	}

	public void applyChanges() {
		if (this.modified) {
			this.currentPositions.clear();
			this.currentPositions.addAll(this.nextPositions);
			this.modified = false;
		}
	}
}
