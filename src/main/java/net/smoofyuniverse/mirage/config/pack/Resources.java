/*
 * Copyright (c) 2018-2024 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.config.pack;

import com.google.common.collect.*;
import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;

public class Resources {
	public static final String GROUND = "Ground", COMMON = "Common", RARE = "Rare";

	public final SetMultimap<String, String> blocks;
	public final Map<String, ConfigurationNode> presets;
	public final ListMultimap<ResourceKey, ConfigurationNode> defaultModifiers;

	public Resources(
			SetMultimap<String, String> blocks,
			Map<String, ConfigurationNode> presets,
			ListMultimap<ResourceKey, ConfigurationNode> defaultModifiers
	) {
		this.blocks = ImmutableSetMultimap.copyOf(blocks);
		this.presets = ImmutableMap.copyOf(presets);
		this.defaultModifiers = ImmutableListMultimap.copyOf(defaultModifiers);
	}

	public static Resources merge(Collection<? extends Resources> collection) {
		if (collection.size() == 1) {
			return collection.iterator().next();
		}

		SetMultimap<String, String> blocks = LinkedHashMultimap.create();
		Map<String, ConfigurationNode> presets = new HashMap<>();
		ListMultimap<ResourceKey, ConfigurationNode> defaultModifiers = ArrayListMultimap.create();

		for (Resources resources : collection) {
			blocks.putAll(resources.blocks);
			presets.putAll(resources.presets);
			defaultModifiers.putAll(resources.defaultModifiers);
		}

		return new Resources(blocks, presets, defaultModifiers);
	}

	public static Set<String> getBlocks(ConfigurationNode node, String... keys) throws SerializationException {
		return Mirage.get().getResources().getAllBlocks(node, keys);
	}

	public static Optional<String> getGround(ConfigurationNode node) throws SerializationException {
		return Mirage.get().getResources().getFirstBlock(node, GROUND);
	}

	public Set<String> getAllBlocks(ConfigurationNode node, String... keys) throws SerializationException {
		Set<String> set = new LinkedHashSet<>();
		for (String key : keys) {
			List<String> names = node.node(key).getList(String.class);
			if (names != null) {
				for (String name : names) {
					set.addAll(this.blocks.get(name));
				}
			}
		}
		return set;
	}

	public Optional<String> getFirstBlock(ConfigurationNode node, String... keys) throws SerializationException {
		for (String key : keys) {
			for (String name : node.node(key).getList(String.class)) {
				Collection<String> col = this.blocks.get(name);
				if (!col.isEmpty()) {
					return Optional.of(col.iterator().next());
				}
			}
		}
		return Optional.empty();
	}
}
