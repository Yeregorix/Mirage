/*
 * Copyright (c) 2018-2021 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.resource;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ListMultimap;
import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.PluginManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Pack implements Comparable<Pack> {
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	public static final int CURRENT_VERSION = 1, MINIMUM_VERSION = 1;

	public final String name;
	public final Set<String> required = new HashSet<>();
	private final Map<String, Section> sections = new HashMap<>();
	private final Collection<Section> unmodSections = Collections.unmodifiableCollection(this.sections.values());
	public int priority;

	public Pack(String name) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name");
		this.name = name;
	}

	public Optional<Section> getSection(String section) {
		if (section == null)
			throw new IllegalArgumentException("dimType");
		return Optional.ofNullable(this.sections.get(section));
	}

	public Optional<Section> removeSection(String section) {
		if (section == null)
			throw new IllegalArgumentException("section");

		Section s = this.sections.remove(section);
		if (s == null)
			return Optional.empty();

		s.parent = null;
		return Optional.of(s);
	}

	public Collection<Section> getSections() {
		return this.unmodSections;
	}

	public void clearSections() {
		this.sections.clear();
	}

	@Override
	public int compareTo(Pack o) {
		return ComparisonChain.start().compare(this.priority, o.priority).compare(this.name, o.name).result();
	}

	public void read(URL url) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), DEFAULT_CHARSET))) {
			read(reader);
		}
	}

	public void read(BufferedReader reader) throws IOException {
		String line = reader.readLine();
		if (line == null)
			throw new IllegalArgumentException("Empty content");

		if (!line.startsWith("version="))
			throw new IllegalArgumentException("Version must be specified in the first line");

		read(Integer.parseInt(line.substring(8).trim()), reader);
	}

	public void read(int version, BufferedReader reader) throws IOException {
		if (version > CURRENT_VERSION || version < MINIMUM_VERSION)
			throw new UnsupportedOperationException("Version is not supported");

		Section section = null;
		List<String> group = null;

		String line;
		while ((line = reader.readLine()) != null) {
			int i = line.indexOf('#');
			if (i != -1)
				line = line.substring(0, i);

			line = line.trim();
			if (line.isEmpty())
				continue;

			if (line.startsWith("require ")) {
				this.required.add(line.substring(8).trim());
				continue;
			}

			if (line.startsWith("priority=")) {
				this.priority = Integer.parseInt(line.substring(9).trim());
				continue;
			}

			if (line.startsWith("section=")) {
				section = getOrCreateSection(line.substring(8).trim());
				continue;
			}

			if (section == null)
				throw new IllegalArgumentException("Section is not defined");

			if (line.startsWith("group=")) {
				group = section.groups.get(line.substring(6).trim());
				continue;
			}

			if (group == null)
				throw new IllegalArgumentException("Group is not defined");

			group.add(line);
		}
	}

	public Section getOrCreateSection(String section) {
		if (section == null)
			throw new IllegalArgumentException("section");

		Section s = this.sections.get(section);
		if (s == null) {
			s = new Section(this, section);
			this.sections.put(section, s);
		}
		return s;
	}

	public void read(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, DEFAULT_CHARSET)) {
			read(reader);
		}
	}

	public static final class Section {
		public final String name;
		public final ListMultimap<String, String> groups = ArrayListMultimap.create();
		private Pack parent;

		private Section(Pack parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		public Optional<Pack> getParent() {
			return Optional.ofNullable(this.parent);
		}

		public void remove() {
			if (this.parent != null)
				this.parent.removeSection(this.name);
		}
	}

	public static TreeSet<Pack> loadAll() {
		TreeSet<Pack> packs = new TreeSet<>();

		URL defaultUrl = Mirage.class.getClassLoader().getResource("default.pack");
		if (defaultUrl != null) {
			Pack p = new Pack("default");
			Mirage.LOGGER.info("Reading default pack ...");
			try {
				p.read(defaultUrl);
				packs.add(p);
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to read default pack", e);
			}
		}

		Path packsDir = Mirage.get().getConfigDirectory().resolve("packs");
		if (!Files.exists(packsDir))
			return packs;

		PluginManager pm = Sponge.pluginManager();
		try (DirectoryStream<Path> st = Files.newDirectoryStream(packsDir)) {
			for (Path file : st) {
				String fn = file.getFileName().toString();

				if (fn.endsWith(".pack")) {
					Pack p = new Pack(fn.substring(0, fn.length() - 5));
					Mirage.LOGGER.info("Reading pack: " + p.name + " ...");
					try {
						p.read(file);

						Set<String> missingMods = new HashSet<>();
						for (String id : p.required) {
							if (!pm.plugin(id).isPresent())
								missingMods.add(id);
						}

						if (!missingMods.isEmpty())
							Mirage.LOGGER.info("The following mods are required to load this pack but are missing: [" + String.join(", ", missingMods) + "]");
						else
							packs.add(p);
					} catch (Exception e) {
						Mirage.LOGGER.error("Failed to read pack: " + p.name, e);
					}
				}
			}
		} catch (Exception e) {
			Mirage.LOGGER.error("Failed to list packs", e);
		}

		return packs;
	}
}
