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

package net.smoofyuniverse.antixray.impl.network.cache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChunkSnapshot {
	public static final int CURRENT_VERSION = 1, MINIMUM_VERSION = 1;

	private BlockContainerSnapshot[] containers;
	private long date;

	public BlockContainerSnapshot[] getContainers() {
		return this.containers;
	}

	public void setContainers(BlockContainerSnapshot[] containers) {
		this.containers = containers;
	}

	public long getDate() {
		return this.date;
	}

	public void setDate(long date) {
		this.date = date;
	}

	public void write(DataOutputStream out) throws IOException {
		out.writeInt(CURRENT_VERSION);
		out.writeLong(this.date);

		out.writeInt(this.containers.length);
		for (BlockContainerSnapshot data : this.containers)
			data.write(out);
	}

	public ChunkSnapshot read(DataInputStream in) throws IOException {
		int version = in.readInt();
		if (version < MINIMUM_VERSION || version > CURRENT_VERSION)
			throw new IllegalArgumentException("version");

		this.date = in.readLong();

		this.containers = new BlockContainerSnapshot[in.readInt()];
		for (int i = 0; i < this.containers.length; i++)
			this.containers[i] = new BlockContainerSnapshot().read(in, version);

		return this;
	}
}
