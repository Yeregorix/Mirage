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

package net.smoofyuniverse.mirage.impl.network.cache;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BlockContainerSnapshot {
	private byte[] blockIds, data, extension, dynamism;
	private int section;

	public int getSection() {
		return this.section;
	}

	public void setSection(int section) {
		this.section = section;
	}

	public byte[] getBlockIds() {
		return this.blockIds;
	}

	public void setBlockIds(byte[] blockIds) {
		if (blockIds == null || blockIds.length != 4096)
			throw new IllegalArgumentException();
		this.blockIds = blockIds;
	}

	public byte[] getData() {
		return this.data;
	}

	public void setData(byte[] data) {
		if (data == null || data.length != 2048)
			throw new IllegalArgumentException();
		this.data = data;
	}

	@Nullable
	public byte[] getExtension() {
		return this.extension;
	}

	public void setExtension(@Nullable byte[] extension) {
		if (extension != null && extension.length != 2048)
			throw new IllegalArgumentException();
		this.extension = extension;
	}

	public byte[] getDynamism() {
		return this.dynamism;
	}

	public void setDynamism(byte[] dynamism) {
		if (dynamism == null || dynamism.length != 2048)
			throw new IllegalArgumentException();
		this.dynamism = dynamism;
	}

	public void write(DataOutputStream out) throws IOException {
		out.writeInt(this.section);
		out.write(this.blockIds);
		out.write(this.data);

		if (this.extension == null)
			out.writeBoolean(false);
		else {
			out.writeBoolean(true);
			out.write(this.extension);
		}

		if (isEmpty(this.dynamism))
			out.writeBoolean(false);
		else {
			out.writeBoolean(true);
			out.write(this.dynamism);
		}
	}

	private static boolean isEmpty(byte[] array) {
		for (byte b : array) {
			if (b != 0)
				return false;
		}
		return true;
	}

	public BlockContainerSnapshot read(DataInputStream in, int version) throws IOException {
		if (version < ChunkSnapshot.MINIMUM_VERSION || version > ChunkSnapshot.CURRENT_VERSION)
			throw new IllegalArgumentException("version");

		this.section = in.readInt();
		this.blockIds = new byte[4096];
		in.readFully(this.blockIds);
		this.data = new byte[2048];
		in.readFully(this.data);

		if (in.readBoolean()) {
			this.extension = new byte[2048];
			in.readFully(this.extension);
		} else
			this.extension = null;

		this.dynamism = new byte[2048];
		if (in.readBoolean())
			in.readFully(this.dynamism);

		return this;
	}
}
