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

package net.smoofyuniverse.mirage.util;

import net.smoofyuniverse.mirage.Mirage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class IOUtil {

	public static Optional<Path> backup(Path file) {
		if (!Files.exists(file))
			return Optional.empty();

		String fn = file.getFileName() + ".backup";
		Path backup;
		int i = 0;
		while (Files.exists(backup = file.resolveSibling(fn + i))) {
			i++;
		}

		try {
			Files.move(file, backup);
		} catch (IOException e) {
			Mirage.LOGGER.warn("Failed to backup: {}", backup, e);
			return Optional.empty();
		}

		return Optional.of(backup);
	}

	public static boolean isEmptyDirectory(Path dir) throws IOException {
		try (DirectoryStream<Path> st = Files.newDirectoryStream(dir)) {
			return !st.iterator().hasNext();
		}
	}
}
