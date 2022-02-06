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

import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.level.chunk.LevelChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.network.NetworkSection;
import net.smoofyuniverse.mirage.impl.network.change.BlockChange;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.math.vector.Vector3i;

import static net.smoofyuniverse.mirage.util.MathUtil.squared;

public final class DynamicSection {
	public final DynamicChunk chunk;
	public final int y;

	private final ShortSet nextPositions = new ShortOpenHashSet();
	private final ShortSet currentPositions = new ShortOpenHashSet();
	private boolean modified;

	private Vector3i relativeCenter;

	DynamicSection(DynamicChunk chunk, int y) {
		this.chunk = chunk;
		this.y = y;
	}

	public Vector3i getRelativeCenter() {
		return this.relativeCenter;
	}

	void setCenter() {
		this.relativeCenter = this.chunk.getRelativeCenter().sub(0, this.y << 4, 0);
	}

	public void add(int x, int y, int z, int distance) {
		if (test(x, y, z, distance))
			add(x, y, z);
	}

	public boolean test(int x, int y, int z, int distance) {
		return this.relativeCenter.distanceSquared(x, y, z) <= squared(distance) << 8;
	}

	public void add(int x, int y, int z) {
		_add(index(x, y, z));
	}

	private void _add(short pos) {
		if (this.nextPositions.add(pos))
			this.modified = true;
	}

	private static short index(int x, int y, int z) {
		return (short) ((x & 15) << 8 | (z & 15) << 4 | (y & 15));
	}

	public void remove(int x, int y, int z) {
		_remove(index(x, y, z));
	}

	private void _remove(short pos) {
		if (this.nextPositions.remove(pos))
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

	public BlockChange.Builder getChanges() {
		BlockChange.Builder b = BlockChange.builder((LevelChunk) this.chunk.storage, this.y);
		getChanges(b);
		return b;
	}

	public void getChanges(BlockChange.Builder b) {
		if (this.modified) {
			InternalSection storage = storage();
			NetworkSection view = storage.view();

			// Reveal
			ShortIterator it = this.nextPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.currentPositions.contains(pos))
					b.add(pos, (BlockState) storage.getBlockState(pos >> 8 & 15, pos & 15, pos >> 4 & 15));
			}

			// Hide
			it = this.currentPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.nextPositions.contains(pos))
					b.add(pos, (BlockState) view.getBlockState(pos >> 8 & 15, pos & 15, pos >> 4 & 15));
			}
		}
	}

	private InternalSection storage() {
		return (InternalSection) this.chunk.storage.getSections()[this.y];
	}

	public BlockChange.Builder getCurrent() {
		BlockChange.Builder b = BlockChange.builder((LevelChunk) this.chunk.storage, this.y);
		getCurrent(b);
		return b;
	}

	public void getCurrent(BlockChange.Builder b) {
		InternalSection storage = storage();

		// Reveal
		ShortIterator it = this.currentPositions.iterator();
		while (it.hasNext()) {
			short pos = it.nextShort();
			b.add(pos, (BlockState) storage.getBlockState(pos >> 8 & 15, pos & 15, pos >> 4 & 15));
		}
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
