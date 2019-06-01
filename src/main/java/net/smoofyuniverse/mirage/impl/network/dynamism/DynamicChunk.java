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
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.change.BlockChange;

import javax.annotation.Nullable;

import static net.smoofyuniverse.mirage.util.MathUtil.lengthSquared;
import static net.smoofyuniverse.mirage.util.MathUtil.squared;

public final class DynamicChunk {
	public final int x, z;

	private ShortSet nextPositions = new ShortOpenHashSet(), currentPositions = new ShortOpenHashSet();
	private boolean modified;

	private Vector3i center;
	private int relCenterX, relCenterY, relCenterZ;

	public DynamicChunk(int x, int z) {
		this.x = x;
		this.z = z;
	}

	@Nullable
	public Vector3i getCenter() {
		return this.center;
	}

	public void setCenter(Vector3i center) {
		this.center = center;
		if (center != null) {
			this.relCenterX = center.getX() - (this.x << 4);
			this.relCenterY = center.getY();
			this.relCenterZ = center.getZ() - (this.z << 4);
		}
	}

	public void update(int x, int y, int z, int distance) {
		if (this.center == null)
			return;

		if (lengthSquared(this.relCenterX - x, this.relCenterY - y, this.relCenterZ - z) <= squared(distance) << 8)
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

	public BlockChange getChanges(InternalChunk chunk, boolean tileEntities) {
		BlockChange.Builder b = BlockChange.builder(chunk);
		getChanges(chunk, b);
		return b.build(tileEntities);
	}

	public void getChanges(InternalChunk chunk, BlockChange.Builder b) {
		if (this.modified) {
			NetworkChunk netChunk = chunk.getView();
			if (netChunk.x != this.x || netChunk.z != this.z)
				throw new IllegalArgumentException("Chunk pos");

			int minX = this.x << 4, minZ = this.z << 4;

			ShortIterator it = this.nextPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.currentPositions.contains(pos))
					b.add(pos, chunk.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
			}

			it = this.currentPositions.iterator();
			while (it.hasNext()) {
				short pos = it.nextShort();
				if (!this.nextPositions.contains(pos))
					b.add(pos, netChunk.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
			}
		}
	}

	public BlockChange getCurrent(InternalChunk chunk, boolean tileEntities) {
		return getCurrent(chunk, 65535, tileEntities);
	}

	public BlockChange getCurrent(InternalChunk chunk, int sections, boolean tileEntities) {
		BlockChange.Builder b = BlockChange.builder(chunk);
		getCurrent(chunk, sections, b);
		return b.build(tileEntities);
	}

	public void getCurrent(InternalChunk chunk, int sections, BlockChange.Builder b) {
		if (sections == 0)
			return;

		NetworkChunk netChunk = chunk.getView();
		if (netChunk.x != this.x || netChunk.z != this.z)
			throw new IllegalArgumentException("Chunk pos");

		int minX = this.x << 4, minZ = this.z << 4;

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

	public void getCurrent(InternalChunk chunk, BlockChange.Builder b) {
		getCurrent(chunk, 65535, b);
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
