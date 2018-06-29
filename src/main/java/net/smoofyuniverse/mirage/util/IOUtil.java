package net.smoofyuniverse.mirage.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IOUtil {

	public static boolean backupFile(Path file) throws IOException {
		if (!Files.exists(file))
			return false;

		String fn = file.getFileName() + ".backup";
		Path backup = null;
		for (int i = 0; i < 100; i++) {
			backup = file.resolveSibling(fn + i);
			if (!Files.exists(backup))
				break;
		}
		Files.move(file, backup);
		return true;
	}
}
