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
import net.smoofyuniverse.mirage.util.BlockResolver;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Pack extends Resources implements Comparable<Pack> {
	public static final int CURRENT_VERSION = 3, MINIMUM_VERSION = 3;

	public final String name;
	public final int priority;

	public Pack(
			String name,
			int priority,
			SetMultimap<String, String> blocks,
			Map<String, ConfigurationNode> presets,
			ListMultimap<ResourceKey, ConfigurationNode> defaultModifiers
	) {
		super(blocks, presets, defaultModifiers);
		this.name = name;
		this.priority = priority;
	}

	@Override
	public int compareTo(Pack o) {
		return ComparisonChain.start().compare(this.priority, o.priority).compare(this.name, o.name).result();
	}

	public static Pack load(String name, ConfigurationNode root) throws SerializationException, PackDisabledException {
		int version = root.node("Version").getInt();
		if (version > CURRENT_VERSION || version < MINIMUM_VERSION)
			throw new UnsupportedOperationException("Version is not supported");

		List<String> requiredMods = root.node("RequiredMods").getList(String.class);
		if (requiredMods != null && !requiredMods.isEmpty()) {
			PluginManager pm = Sponge.pluginManager();

			Set<String> missingMods = new HashSet<>();
			for (String id : requiredMods) {
				if (pm.plugin(id).isEmpty())
					missingMods.add(id);
			}

			if (!missingMods.isEmpty())
				throw new PackDisabledException("This pack requires missing mods: " + String.join(", ", missingMods));
		}

		int priority = root.node("Priority").getInt();

		SetMultimap<String, String> blocks = LinkedHashMultimap.create();
		BlockResolver resolver = new BlockResolver();
		for (Entry<Object, ? extends ConfigurationNode> e : root.node("Blocks").childrenMap().entrySet()) {
			Set<String> set = blocks.get((String) e.getKey());
			for (String input : e.getValue().getList(String.class)) {
				if (resolver.resolve(input)) {
					set.add(input);
				}
			}
		}
		resolver.flushErrors();

		Map<String, ConfigurationNode> presets = (Map) root.node("Presets").childrenMap();

		ListMultimap<ResourceKey, ConfigurationNode> defaultModifiers = ArrayListMultimap.create();
		for (Entry<Object, ? extends ConfigurationNode> e : root.node("DefaultModifiers").childrenMap().entrySet()) {
			defaultModifiers.putAll(ResourceKey.resolve((String) e.getKey()), e.getValue().childrenList());
		}

		return new Pack(name, priority, blocks, presets, defaultModifiers);
	}
}
