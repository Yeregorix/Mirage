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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.WorldType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class Resources {
	public static final String GROUND = "Ground", COMMON = "Common", RARE = "Rare";

	public final Set<ResourceKey> worldTypes;
	public final Multimap<String, String> blocks;
	public final String ground;

	public Resources(ResourceKey worldType) {
		this(worldType, ArrayListMultimap.create());
	}

	public Resources(ResourceKey worldType, Multimap<String, String> blocks) {
		this(ImmutableSet.of(worldType), blocks);
	}

	public Resources(Collection<ResourceKey> worldTypes, Multimap<String, String> blocks) {
		this.worldTypes = ImmutableSet.copyOf(worldTypes);

		Collection<String> groundBlocks = blocks.get(GROUND);
		if (groundBlocks.isEmpty()) {
			this.ground = "minecraft:stone";
			groundBlocks.add(this.ground);
		} else {
			this.ground = groundBlocks.iterator().next();
		}

		this.blocks = ImmutableMultimap.copyOf(blocks);
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

	public static Resources of(WorldType worldType) {
		return Mirage.get().getResources(worldType.key(RegistryTypes.WORLD_TYPE));
	}
}
