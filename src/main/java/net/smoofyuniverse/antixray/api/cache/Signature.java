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

package net.smoofyuniverse.antixray.api.cache;

import net.smoofyuniverse.antixray.util.collection.WeightedList;
import org.spongepowered.api.CatalogType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * This object is used to hold a bytes array as a signature
 */
public class Signature {
	public static final String DEFAULT_DIGEST_ALGORITHM = "sha-1";

	private static final Signature EMPTY = new Signature(new byte[0]);
	private static final char[] hexchars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	private final byte[] bytes;

	public Signature(byte[] bytes) {
		if (bytes.length > Short.MAX_VALUE)
			throw new IllegalArgumentException("Max length: " + Short.MAX_VALUE);
		this.bytes = bytes;
	}

	public void write(DataOutputStream out) throws IOException {
		out.writeShort(this.bytes.length);
		out.write(this.bytes);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Signature && Arrays.equals(this.bytes, ((Signature) obj).bytes);
	}

	@Override
	public String toString() {
		return toHexString(this.bytes);
	}

	public static Signature read(DataInputStream in) throws IOException {
		byte[] bytes = new byte[in.readShort()];
		in.readFully(bytes);
		return new Signature(bytes);
	}

	public static Signature empty() {
		return EMPTY;
	}

	public static String toHexString(byte[] bytes) {
		StringBuilder s = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			s.append(hexchars[(b & 0xF0) >> 4]).append(hexchars[b & 0x0F]);
		return s.toString();
	}

	public static Builder builder() {
		return builder(DEFAULT_DIGEST_ALGORITHM);
	}

	public static Builder builder(String algorithm) {
		return new Builder(algorithm);
	}

	public static class Builder {
		private MessageDigest message;
		private byte[] buffer = new byte[8];

		private Builder(String algorithm) {
			try {
				this.message = MessageDigest.getInstance(algorithm);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

		public Builder reset() {
			this.message.reset();
			return this;
		}

		public Builder append(byte value) {
			this.message.update(value);
			return this;
		}

		public Builder append(short value) {
			this.buffer[0] = (byte) (value >>> 8);
			this.buffer[1] = (byte) value;
			appendBuffer(2);
			return this;
		}

		private void appendBuffer(int length) {
			this.message.update(this.buffer, 0, length);
		}

		public Builder append(float value) {
			return append(Float.floatToIntBits(value));
		}

		public Builder append(int value) {
			this.buffer[0] = (byte) (value >>> 24);
			this.buffer[1] = (byte) (value >>> 16);
			this.buffer[2] = (byte) (value >>> 8);
			this.buffer[3] = (byte) value;
			appendBuffer(4);
			return this;
		}

		public Builder append(double value) {
			return append(Double.doubleToLongBits(value));
		}

		public Builder append(long value) {
			this.buffer[0] = (byte) (value >>> 56);
			this.buffer[1] = (byte) (value >>> 48);
			this.buffer[2] = (byte) (value >>> 40);
			this.buffer[3] = (byte) (value >>> 32);
			this.buffer[4] = (byte) (value >>> 24);
			this.buffer[5] = (byte) (value >>> 16);
			this.buffer[6] = (byte) (value >>> 8);
			this.buffer[7] = (byte) value;
			appendBuffer(8);
			return this;
		}

		public Builder append(Iterable<? extends CatalogType> it) {
			for (CatalogType type : it)
				append(type);
			return this;
		}

		public Builder append(CatalogType type) {
			return type == null ? append(0) : append(type.getId());
		}

		public Builder append(String value) {
			int strlen = value.length();
			int utflen = 0;
			int c, count = 0;

			for (int i = 0; i < strlen; i++) {
				c = value.charAt(i);
				if ((c >= 0x0001) && (c <= 0x007F)) {
					utflen++;
				} else if (c > 0x07FF) {
					utflen += 3;
				} else {
					utflen += 2;
				}
			}

			if (utflen > 65535)
				throw new IllegalArgumentException("Encoded string too long: " + utflen + " bytes");

			byte[] bytearr = new byte[utflen];

			for (int i = 0; i < strlen; i++) {
				c = value.charAt(i);
				if ((c >= 0x0001) && (c <= 0x007F)) {
					bytearr[count++] = (byte) c;
				} else if (c > 0x07FF) {
					bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
					bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
					bytearr[count++] = (byte) (0x80 | (c & 0x3F));
				} else {
					bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
					bytearr[count++] = (byte) (0x80 | (c & 0x3F));
				}
			}

			return append(bytearr);
		}

		public Builder append(byte[] bytes) {
			return append(bytes, 0, bytes.length);
		}

		public Builder append(byte[] bytes, int offset, int length) {
			this.message.update(bytes, offset, length);
			return this;
		}

		public Builder append(WeightedList<? extends CatalogType> list) {
			list.forEach((b, w) -> append(b).append(w));
			return this;
		}

		public Signature build() {
			return new Signature(this.message.digest());
		}
	}
}
