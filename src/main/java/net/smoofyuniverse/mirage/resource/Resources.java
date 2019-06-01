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

package net.smoofyuniverse.mirage.resource;

import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.resource.Pack.Section;
import net.smoofyuniverse.mirage.util.IOUtil;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.storage.WorldProperties;

import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

import static net.smoofyuniverse.mirage.resource.Categories.GROUND;

public final class Resources {
	private static final Map<DimensionType, Resources> map = new HashMap<>();

	private final Map<String, BlockSet> blocks = new HashMap<>();
	private BlockState ground;

	private Resources() {}

	public BlockState getGround() {
		return this.ground;
	}

	public static Resources of(DimensionType type) {
		if (type == null)
			throw new IllegalArgumentException("type");

		Resources r = map.get(type);
		if (r == null) {
			Mirage.LOGGER.warn("Unregistered resources for dimension type: " + type.getId());
			r = new Resources();
			r.setGround();
			map.put(type, r);
		}
		return r;
	}

	public BlockSet getBlocks(String category) {
		BlockSet set = this.blocks.get(category);
		return set == null ? new BlockSet() : set.copy();
	}

	public BlockSet getBlocks(String... categories) {
		BlockSet col = null;

		for (String c : categories) {
			BlockSet set = this.blocks.get(c);
			if (set != null) {
				if (col == null)
					col = set.copy();
				else
					col.add(set);
			}
		}

		return col == null ? new BlockSet() : col;
	}

	public void getBlocks(BlockSet col, String category) {
		BlockSet set = this.blocks.get(category);
		if (set != null)
			col.add(set);
	}

	public void getBlocks(BlockSet col, String... categories) {
		for (String c : categories) {
			BlockSet set = this.blocks.get(c);
			if (set != null)
				col.add(set);
		}
	}

	public static Resources of(WorldProperties world) {
		return of(world.getDimensionType());
	}

	private void setGround() {
		BlockSet set = this.blocks.get(GROUND);
		this.ground = set == null ? null : set.first().orElse(null);

		if (this.ground == null) {
			this.ground = BlockTypes.STONE.getDefaultState();
			set = new BlockSet();
			set.add(this.ground);
			this.blocks.put(GROUND, set);
		}
	}

	public static void loadResources() {
		loadResources(loadPacks());
	}

	public static void loadResources(TreeSet<Pack> packs) {
		map.clear();

		GameRegistry reg = Sponge.getRegistry();
		for (DimensionType type : reg.getAllOf(DimensionType.class)) {
			Mirage.LOGGER.info("Loading resources for dimension type: " + type.getId() + " ..");
			Resources r = new Resources();

			for (Pack p : packs) {
				Section s = p.getSection(type.getId()).orElse(null);
				if (s == null)
					continue;

				Mirage.LOGGER.debug("Loading resources from pack: " + p.name + " ..");

				for (Entry<String, Collection<String>> e : s.groups.asMap().entrySet()) {
					BlockSet set = r.blocks.get(e.getKey());
					if (set == null) {
						set = new BlockSet();
						r.blocks.put(e.getKey(), set);
					}

					set.deserialize(e.getValue(), true);
				}
			}

			r.setGround();
			map.put(type, r);
		}
	}

	private static TreeSet<Pack> loadPacks() {
		TreeSet<Pack> packs = new TreeSet<>();

		URL defaultUrl = IOUtil.getLocalResource("default.pack").orElse(null);
		if (defaultUrl != null) {
			Pack p = new Pack("default");
			Mirage.LOGGER.info("Reading default pack ..");
			try {
				p.read(defaultUrl);
				packs.add(p);
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to read default pack", e);
			}
		}

		PluginManager pm = Sponge.getPluginManager();

		try (DirectoryStream<Path> st = Files.newDirectoryStream(Mirage.get().getResourcesDirectory())) {
			for (Path file : st) {
				String fn = file.getFileName().toString();

				if (fn.endsWith(".pack")) {
					Pack p = new Pack(fn.substring(0, fn.length() - 5));
					Mirage.LOGGER.info("Reading pack: " + p.name + " ..");
					try {
						p.read(file);

						Set<String> missingMods = new HashSet<>();
						for (String id : p.required) {
							if (!pm.isLoaded(id))
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
