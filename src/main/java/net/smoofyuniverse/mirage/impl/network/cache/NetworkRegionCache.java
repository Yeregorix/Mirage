/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public class NetworkRegionCache {
	public static final int CURRENT_VERSION = 3;

	public final Path directory;
	public final String name;

	private final RegionFileStorage storage;
	private long seed;

	public NetworkRegionCache(String name) {
		this(Mirage.get().getCacheDirectory().resolve(name), name);
	}

	public NetworkRegionCache(Path directory, String name) {
		this.directory = directory;
		this.name = name;
		this.storage = new RegionFileStorage(directory.toFile(), false);
	}

	public long getSeed() {
		return this.seed;
	}

	public void load() throws Exception {
		// Directory
		try {
			Files.createDirectories(this.directory);
		} catch (IOException ignored) {
		}

		// Version
		int version;
		Path versionFile = this.directory.resolve("version");
		if (Files.exists(versionFile)) {
			try (DataInputStream in = new DataInputStream(Files.newInputStream(versionFile))) {
				version = in.readInt();
			}
		} else if (IOUtil.isEmptyDirectory(this.directory)) {
			version = CURRENT_VERSION;
		} else {
			throw new IllegalArgumentException("directory is not empty");
		}

		if (version != CURRENT_VERSION) {
			Mirage.LOGGER.info("Deleting outdated cache " + this.name + "/ ...");

			close();
			deleteRegionFiles();
			version = CURRENT_VERSION;
		}

		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(versionFile))) {
			out.writeInt(version);
		}

		// Seed
		Path seedFile = this.directory.resolve("seed");
		if (Files.exists(seedFile)) {
			try (DataInputStream in = new DataInputStream(Files.newInputStream(seedFile))) {
				this.seed = in.readLong();
			}
		} else {
			this.seed = ThreadLocalRandom.current().nextLong();
			try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(seedFile))) {
				out.writeLong(version);
			}
		}
	}

	public void close() throws IOException {
		this.storage.close();
		this.storage.regionCache.clear();
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

	private void deleteRegionFiles() throws IOException {
		ExceptionCollector<IOException> errors = new ExceptionCollector<>();

		try (DirectoryStream<Path> st = Files.newDirectoryStream(this.directory)) {
			for (Path p : st) {
				if (isRegionFile(p.getFileName().toString())) {
					try {
						Files.delete(p);
					} catch (IOException e) {
						errors.add(e);
					}
				}
			}
		} catch (DirectoryIteratorException e) {
			throw e.getCause();
		}

		errors.throwIfPresent();
	}

	public void flush() throws IOException {
		this.storage.flush();
	}

	public CompoundTag read(int x, int z) throws IOException {
		return this.storage.read(new ChunkPos(x, z));
	}

	public void write(int x, int z, CompoundTag data) throws IOException {
		this.storage.write(new ChunkPos(x, z), data);
	}
}
