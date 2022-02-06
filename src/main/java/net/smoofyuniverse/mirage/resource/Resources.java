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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.resource.Pack.Section;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.WorldType;

import java.util.*;
import java.util.Map.Entry;

import static net.smoofyuniverse.mirage.resource.Categories.GROUND;

public final class Resources {
	public static final Resources DEFAULT = new Resources(ArrayListMultimap.create());
	private static final Map<WorldType, Resources> map = new HashMap<>();

	private final Multimap<String, String> blocks;
	private final String ground;

	private Resources(Multimap<String, String> blocks) {
		this.blocks = blocks;

		Collection<String> groundCol = this.blocks.get(GROUND);
		if (groundCol.isEmpty()) {
			this.ground = "minecraft:stone";
			groundCol.add(this.ground);
		} else {
			this.ground = groundCol.iterator().next();
		}
	}

	public Set<String> getBlocks(String... categories) {
		Set<String> set = new HashSet<>();
		getBlocks(set, categories);
		return set;
	}

	public void getBlocks(Collection<String> col, String... categories) {
		for (String category : categories)
			col.addAll(this.blocks.get(category));
	}

	public String getGround() {
		return this.ground;
	}

	public static Resources of(WorldType type) {
		if (type == null)
			throw new IllegalArgumentException("type");

		Resources r = map.get(type);
		if (r == null) {
			Mirage.LOGGER.warn("Unregistered resources for world type: " + type.key(RegistryTypes.WORLD_TYPE));
			r = DEFAULT;
			map.put(type, r);
		}
		return r;
	}

	public static void loadResources(Iterable<Pack> packs) {
		map.clear();

		Registry<WorldType> worldTypeRegistry = RegistryTypes.WORLD_TYPE.get();
		worldTypeRegistry.streamEntries().forEach(entry -> {
			Mirage.LOGGER.info("Loading resources for dimension type: " + entry.key() + " ...");
			Multimap<String, String> blocks = LinkedHashMultimap.create();

			for (Pack p : packs) {
				Section s = p.getSection(entry.key().formatted()).orElse(null);
				if (s == null)
					continue;

				Mirage.LOGGER.debug("Loading resources from pack: " + p.name + " ...");

				for (Entry<String, Collection<String>> e : s.groups.asMap().entrySet()) {
					blocks.putAll(e.getKey(), e.getValue());
				}
			}

			map.put(entry.value(), new Resources(blocks));
		});
	}
}
