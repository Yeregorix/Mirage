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

package net.smoofyuniverse.mirage.impl.network.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class NetworkRegionCache {
	public static final int CURRENT_VERSION = 2, MINIMUM_VERSION = 1;

	private final Long2ObjectMap<NetworkRegionFile> loadedRegions = new Long2ObjectOpenHashMap<>();
	private final Path dir;
	private int version = -1;

	public NetworkRegionCache(Path dir) {
		this.dir = dir;
		try {
			Files.createDirectories(this.dir);
		} catch (IOException ignored) {
		}
	}

	public void loadVersion() throws IOException {
		if (this.version == -1) {
			Path file = this.dir.resolve("version");
			if (Files.exists(file)) {
				try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
					this.version = in.readInt();
				}
			} else {
				try (DirectoryStream<Path> st = Files.newDirectoryStream(this.dir)) {
					this.version = st.iterator().hasNext() ? 0 : CURRENT_VERSION;
				}
			}
		}
	}

	public int getVersion() {
		return this.version;
	}

	public boolean isVersionSupported() {
		return this.version >= MINIMUM_VERSION && this.version <= CURRENT_VERSION;
	}

	public boolean shouldUpdateVersion() {
		return this.version != CURRENT_VERSION;
	}

	public void updateVersion() throws IOException {
		if (this.version < MINIMUM_VERSION || this.version > CURRENT_VERSION)
			throw new UnsupportedOperationException();

		if (this.version == CURRENT_VERSION)
			return;

		synchronized (this.loadedRegions) {
			if (!this.loadedRegions.isEmpty())
				throw new IllegalStateException();
		}

		if (this.version == 1) {
			deleteRegionFiles();
			this.version = 2;
		}
	}

	private void deleteRegionFiles() throws IOException {
		IOException error = null;

		try (DirectoryStream<Path> st = Files.newDirectoryStream(this.dir)) {
			for (Path p : st) {
				if (isRegionFile(p.getFileName().toString())) {
					try {
						Files.delete(p);
					} catch (IOException e) {
						error = e;
					}
				}
			}
		} catch (DirectoryIteratorException e) {
			throw e.getCause();
		}

		if (error != null)
			throw error;
	}

	public void saveVersion() throws IOException {
		Path file = this.dir.resolve("version");
		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
			out.writeInt(this.version);
		}
	}

	@Nullable
	public NetworkRegionFile getIfLoaded(int x, int z) {
		synchronized (this.loadedRegions) {
			return this.loadedRegions.get(NetworkChunk.asLong(x, z));
		}
	}

	@Nullable
	public DataInputStream getChunkInputStream(int cx, int cz) {
		NetworkRegionFile file = getOrLoad(cx >> 5, cz >> 5);
		return file == null ? null : file.getDataInputStream(cx & 31, cz & 31);
	}

	@Nullable
	public NetworkRegionFile getOrLoad(int x, int z) {
		synchronized (this.loadedRegions) {
			long pos = NetworkChunk.asLong(x, z);
			NetworkRegionFile file = this.loadedRegions.get(pos);
			if (file != null)
				return file;

			Path p = getRegionFile(x, z);
			if (!Files.exists(p))
				return null;

			if (this.loadedRegions.size() >= 32)
				closeLoadedRegions();

			file = new NetworkRegionFile(p);
			this.loadedRegions.put(pos, file);
			return file;
		}
	}

	private Path getRegionFile(int x, int z) {
		return this.dir.resolve("r." + x + "." + z + ".dat");
	}

	private static boolean isRegionFile(String name) {
		if (name.startsWith("r.") && name.endsWith(".dat")) {
			String pos = name.substring(2, name.length() - 4);
			int i = pos.indexOf('.');
			if (i == -1)
				return false;

			try {
				Integer.parseInt(pos.substring(0, i));
				Integer.parseInt(pos.substring(i + 1));
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	public void closeLoadedRegions() {
		synchronized (this.loadedRegions) {
			for (NetworkRegionFile file : this.loadedRegions.values()) {
				try {
					file.close();
				} catch (IOException e) {
					Mirage.LOGGER.warn("Failed to close region file " + file.getFile().getFileName(), e);
				}
			}
			this.loadedRegions.clear();
		}
	}

	public DataOutputStream getChunkOutputStream(int cx, int cz) {
		return getOrCreate(cx >> 5, cz >> 5).getDataOutputStream(cx & 31, cz & 31);
	}

	public NetworkRegionFile getOrCreate(int x, int z) {
		synchronized (this.loadedRegions) {
			long pos = NetworkChunk.asLong(x, z);
			NetworkRegionFile file = this.loadedRegions.get(pos);
			if (file != null)
				return file;

			if (this.loadedRegions.size() >= 32)
				closeLoadedRegions();

			file = new NetworkRegionFile(getRegionFile(x, z));
			this.loadedRegions.put(pos, file);
			return file;
		}
	}
}
