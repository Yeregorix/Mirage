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
import java.security.SecureRandom;

public class NetworkRegionCache {
	public static final int CURRENT_VERSION = 5;

	public final Path directory;
	public final String name;

	private final RegionFileStorage storage;
	private long obfuscationSeed, fakeSeed;

	public NetworkRegionCache(String name) {
		this(Mirage.get().getCacheDirectory().resolve(name), name);
	}

	public NetworkRegionCache(Path directory, String name) {
		this.directory = directory;
		this.name = name;
		this.storage = new RegionFileStorage(directory, false);
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
				return true;
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	public long getObfuscationSeed() {
		return this.obfuscationSeed;
	}

	public long getFakeSeed() {
		return this.fakeSeed;
	}

	public void close() throws IOException {
		this.storage.close();
		this.storage.regionCache.clear();
	}

	public void load() throws Exception {
		try {
			Files.createDirectories(this.directory);
		} catch (IOException ignored) {
		}

		int version;
		long obfuscationSeed, fakeSeed;

		Path file = this.directory.resolve("cache.dat");
		if (Files.exists(file)) {
			try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
				version = in.readInt();
				obfuscationSeed = in.readLong();
				fakeSeed = version >= 4 ? in.readLong() : 0;
			}
		} else if (IOUtil.isEmptyDirectory(this.directory)) {
			version = CURRENT_VERSION;
			SecureRandom r = new SecureRandom();
			obfuscationSeed = r.nextLong();
			fakeSeed = r.nextLong();
		} else {
			throw new IllegalArgumentException("Cache directory is not empty");
		}

		if (version < 4 || version > CURRENT_VERSION) {
			Mirage.LOGGER.info("Deleting outdated cache " + this.name + "/ ...");

			close();
			deleteRegionFiles();
		}

		if (version < 4) {
			fakeSeed = new SecureRandom().nextLong();
		}

		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
			out.writeInt(CURRENT_VERSION);
			out.writeLong(obfuscationSeed);
			out.writeLong(fakeSeed);
		}

		this.obfuscationSeed = obfuscationSeed;
		this.fakeSeed = fakeSeed;
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
