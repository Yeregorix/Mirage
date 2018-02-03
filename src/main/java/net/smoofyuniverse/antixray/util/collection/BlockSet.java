/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.antixray.util.collection;

import com.google.common.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;

import java.util.*;

public final class BlockSet {
	public static final TypeToken<BlockSet> TOKEN = TypeToken.of(BlockSet.class);

	private final Object2BooleanMap<BlockState> states = new Object2BooleanOpenHashMap<>(), unmodState = Object2BooleanMaps.unmodifiable(this.states);
	private final Set<BlockType> types = new HashSet<>(), unmodTypes = Collections.unmodifiableSet(this.types);

	public Object2BooleanMap<BlockState> getInternalStates() {
		return this.unmodState;
	}

	public Set<BlockType> getInternalTypes() {
		return this.unmodTypes;
	}

	public Set<BlockState> toSet() {
		Set<BlockState> set = new HashSet<>();

		for (BlockType type : this.types)
			set.addAll(type.getAllBlockStates());

		for (Entry<BlockState> e : this.states.object2BooleanEntrySet()) {
			if (e.getBooleanValue())
				set.add(e.getKey());
			else
				set.remove(e.getKey());
		}

		return set;
	}

	public List<String> toStringList() {
		List<String> list = new ArrayList<>();

		for (BlockType type : this.types)
			list.add(type.getId());

		for (Entry<BlockState> e : this.states.object2BooleanEntrySet()) {
			String id = e.getKey().getId();
			if (!e.getBooleanValue())
				id = "-" + id;
			list.add(id);
		}

		return list;
	}

	public void fromStringList(List<String> list) {
		clear();
		GameRegistry reg = Sponge.getRegistry();

		for (String id : list) {
			boolean value = id.charAt(0) != '-';
			if (!value)
				id = id.substring(1);

			BlockType type = reg.getType(BlockType.class, id).orElse(null);
			if (type != null) {
				if (value)
					add(type);
				else
					remove(type);
				continue;
			}

			BlockState state = reg.getType(BlockState.class, id).orElse(null);
			if (state != null) {
				if (value)
					add(state);
				else
					remove(state);
				continue;
			}

			throw new IllegalArgumentException("Id '" + id + "' is not a valid BlockType or BlockState");
		}
	}

	public void clear() {
		this.states.clear();
		this.types.clear();
	}

	public void add(BlockType type) {
		for (BlockState state : type.getAllBlockStates())
			this.states.remove(state);
		this.types.add(type);
	}

	public void remove(BlockType type) {
		for (BlockState state : type.getAllBlockStates())
			this.states.remove(state);
		this.types.remove(type);
	}

	public void add(BlockState state) {
		if (this.types.contains(state.getType()))
			this.states.remove(state);
		else
			this.states.put(state, true);
	}

	public void remove(BlockState state) {
		if (this.types.contains(state.getType()))
			this.states.put(state, false);
		else
			this.states.remove(state);
	}
}
