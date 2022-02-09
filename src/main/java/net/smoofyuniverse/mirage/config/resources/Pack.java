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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import net.smoofyuniverse.mirage.util.BlockResolver;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public final class Pack implements Comparable<Pack> {
	public static final int CURRENT_VERSION = 2, MINIMUM_VERSION = 2;

	public final String name;
	public final int priority;
	public final List<Resources> resources;

	public Pack(String name, int priority, Collection<Resources> resources) {
		this.name = name;
		this.priority = priority;
		this.resources = ImmutableList.copyOf(resources);
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
				if (!pm.plugin(id).isPresent())
					missingMods.add(id);
			}

			if (!missingMods.isEmpty())
				throw new PackDisabledException("This pack requires missing mods: " + String.join(", ", missingMods));
		}

		int priority = root.node("Priority").getInt();

		ImmutableList.Builder<Resources> resources = ImmutableList.builder();
		BlockResolver resolver = new BlockResolver();

		for (ConfigurationNode node : root.node("Resources").childrenList()) {
			List<ResourceKey> worldTypes = node.node("WorldTypes").getList(ResourceKey.class);

			SetMultimap<String, String> blocks = LinkedHashMultimap.create();
			for (Entry<Object, ? extends ConfigurationNode> e : node.node("Blocks").childrenMap().entrySet()) {
				Set<String> set = blocks.get((String) e.getKey());
				for (String input : e.getValue().getList(String.class)) {
					if (resolver.resolve(input))
						set.add(input);
				}
			}

			resources.add(new Resources(worldTypes, blocks));
		}

		resolver.flushErrors();
		return new Pack(name, priority, resources.build());
	}
}
