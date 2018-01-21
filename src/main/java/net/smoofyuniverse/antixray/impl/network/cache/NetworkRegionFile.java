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

package net.smoofyuniverse.antixray.impl.network.cache;

import net.minecraft.world.chunk.storage.RegionFile;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class NetworkRegionFile {
	private RegionFile delegate;
	private boolean closed;
	private Path file;

	public NetworkRegionFile(Path file) {
		this.delegate = new RegionFile(file.toFile());
		this.file = file;
	}

	public Path getFile() {
		return this.file;
	}

	public boolean exists(int x, int z) {
		checkBounds(x, z);
		return this.delegate.isChunkSaved(x, z);
	}

	private void checkBounds(int x, int z) {
		if (!contains(x, z))
			throw new IllegalArgumentException();
	}

	public boolean contains(int x, int z) {
		return x >= 0 && z >= 0 && x < 32 && z < 32;
	}

	@Nullable
	public DataInputStream getDataInputStream(int x, int z) {
		if (this.closed)
			throw new IllegalStateException("Closed");
		checkBounds(x, z);
		return this.delegate.getChunkDataInputStream(x, z);
	}

	public DataOutputStream getDataOutputStream(int x, int z) {
		if (this.closed)
			throw new IllegalStateException("Closed");
		checkBounds(x, z);
		return this.delegate.getChunkDataOutputStream(x, z);
	}

	public boolean isClosed() {
		return this.closed;
	}

	public void close() throws IOException {
		if (!this.closed) {
			this.delegate.close();
			this.closed = true;
		}
	}
}
