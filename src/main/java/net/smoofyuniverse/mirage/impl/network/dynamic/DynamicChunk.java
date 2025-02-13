/*
 * Copyright (c) 2018-2025 Hugo Dupanloup (Yeregorix)
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

import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkSection;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import org.spongepowered.math.vector.Vector3i;

import static net.smoofyuniverse.mirage.util.MathUtil.lengthSquared;
import static net.smoofyuniverse.mirage.util.MathUtil.squared;
import static org.spongepowered.math.GenericMath.clamp;

public final class DynamicChunk {
	public static final int maxDistance2 = squared(160);

	public final DynamicWorld world;
	public final InternalChunk storage;

	public final DynamicSection[] sections;
	public final int minSectionY;
	private Vector3i relativeCenter;

	DynamicChunk(DynamicWorld world, InternalChunk storage) {
		this.world = world;
		this.storage = storage;
		this.sections = new DynamicSection[storage.getSectionsCount()];
		this.minSectionY = storage.getMinSectionY();
	}

	public Vector3i getRelativeCenter() {
		return this.relativeCenter;
	}

	void updateCenter() {
		Vector3i min = this.storage.min();
		this.relativeCenter = this.world.getCenter().sub(min.x(), 0, min.z());
		int x = this.relativeCenter.x(), z = this.relativeCenter.z();
		int xzDistance2 = lengthSquared(clamp(x, 0, 15) - x, clamp(z, 0, 15) - z);

		NetworkChunk chunkView = this.storage.view();
		for (int i = 0; i < this.sections.length; i++) {
			DynamicSection section = this.sections[i];
			NetworkSection view = chunkView.sections[i];

			if (section == null) {
				if (view.hasNoDynamism()) {
					continue;
				}

				section = new DynamicSection(this, this.minSectionY + i);
				this.sections[i] = section;
			}

			section.setCenter();
			section.clear();

			int y = section.getRelativeCenter().y();
			int d2 = xzDistance2 + squared(clamp(y, 0, 15) - y);
			if (d2 <= maxDistance2) {
				if (d2 <= squared(view.getMaxDynamism()) << 8) {
					view.collectDynamicPositions(section);
				}
			}
		}

		ChunkChangeListener listener = chunkView.getListener();
		if (listener != null)
			listener.markChanged();
	}

	public void clear() {
		for (DynamicSection section : this.sections) {
			if (section != null)
				section.clear();
		}
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
		int sectionY = y >> 4;
		int i = sectionY - this.minSectionY;
		DynamicSection section = this.sections[i];
		if (section == null) {
			section = new DynamicSection(this, sectionY);
			this.sections[i] = section;
			section.setCenter();
		}
		section.add(x, y & 15, z);
	}

	public void remove(int x, int y, int z) {
		int i = (y >> 4) - this.minSectionY;
		DynamicSection section = this.sections[i];
		if (section != null)
			section.remove(x, y & 15, z);
	}
}
