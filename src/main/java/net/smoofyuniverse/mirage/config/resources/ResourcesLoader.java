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

package net.smoofyuniverse.mirage.config.resources;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.ResourceKey;

import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ResourcesLoader {
	private final Mirage mirage;
	private final TreeSet<Pack> packs = new TreeSet<>();

	public ResourcesLoader(Mirage mirage) {
		this.mirage = mirage;
	}

	public void addDefault() {
		URL url = Mirage.class.getClassLoader().getResource("default.conf");
		if (url != null) {
			Mirage.LOGGER.info("Loading default pack ...");
			try {
				this.packs.add(Pack.load("default", this.mirage.createConfigLoader(url).load()));
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to load default pack", e);
			}
		}
	}

	public void addDirectory(Path directory) {
		if (!Files.exists(directory))
			return;

		try (DirectoryStream<Path> st = Files.newDirectoryStream(directory)) {
			for (Path file : st) {
				String fn = file.getFileName().toString();
				if (fn.endsWith(".conf")) {
					String name = fn.substring(0, fn.length() - 5);

					Mirage.LOGGER.info("Loading pack {} ...", name);
					try {
						this.packs.add(Pack.load(name, this.mirage.createConfigLoader(file).load()));
					} catch (PackDisabledException e) {
						Mirage.LOGGER.info(e.getMessage());
					} catch (Exception e) {
						Mirage.LOGGER.error("Failed to load pack {}", name, e);
					}
				}
			}
		} catch (Exception e) {
			Mirage.LOGGER.error("Failed to list packs", e);
		}
	}

	public Map<ResourceKey, Resources> build() {
		Set<ResourceKey> worldTypes = new HashSet<>();
		for (Pack pack : this.packs) {
			for (Resources r : pack.resources)
				worldTypes.addAll(r.worldTypes);
		}

		Map<ResourceKey, Resources> map = new HashMap<>();
		for (ResourceKey key : worldTypes) {
			Mirage.LOGGER.debug("Building resources for world type {} ...", key);
			Multimap<String, String> blocks = LinkedHashMultimap.create();

			for (Pack pack : this.packs) {
				for (Resources r : pack.resources) {
					if (r.worldTypes.contains(key))
						blocks.putAll(r.blocks);
				}
			}

			map.put(key, new Resources(key, blocks));
		}

		return map;
	}
}
