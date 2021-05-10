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

package net.smoofyuniverse.mirage.util;

import com.google.common.reflect.TypeToken;
import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class BlockSet {
	public static final TypeToken<BlockSet> TOKEN = TypeToken.of(BlockSet.class);

	private final Set<BlockState> states = new LinkedHashSet<>(), unmodState = Collections.unmodifiableSet(this.states);

	public Set<BlockState> getAll() {
		return this.unmodState;
	}

	public Optional<BlockState> first() {
		return this.states.isEmpty() ? Optional.empty() : Optional.of(this.states.iterator().next());
	}

	public void serialize(Collection<String> col, SerializationPredicate predicate) {
		for (Map.Entry<BlockType, List<BlockState>> e : asMap().entrySet()) {
			BlockType t = e.getKey();
			List<BlockState> l = e.getValue();

			if (predicate.mode(l.size(), t.getAllBlockStates().size())) {
				col.add(t.getId());

				for (BlockState b : t.getAllBlockStates()) {
					if (!l.contains(b))
						col.add("-" + b.getId());
				}
			} else {
				for (BlockState b : l)
					col.add(b.getId());
			}
		}
	}

	public Map<BlockType, List<BlockState>> asMap() {
		Map<BlockType, List<BlockState>> map = new LinkedHashMap<>();

		for (BlockState state : this.states) {
			List<BlockState> set = map.get(state.getType());
			if (set == null) {
				set = new ArrayList<>();
				map.put(state.getType(), set);
			}
			set.add(state);
		}

		return map;
	}

	public void deserialize(Collection<String> col, boolean skipErrors) {
		GameRegistry reg = Sponge.getRegistry();

		for (String id : col) {
			boolean value = !id.startsWith("-");
			if (!value)
				id = id.substring(1);

			if (id.startsWith("regex!")) {
				id = id.substring(6);
				Pattern pattern;
				try {
					pattern = Pattern.compile(id, Pattern.CASE_INSENSITIVE);
				} catch (PatternSyntaxException e) {
					if (skipErrors) {
						Mirage.LOGGER.warn("String '" + id + "' is not a valid pattern", e);
						continue;
					}
					throw new IllegalArgumentException("String '" + id + "' is not a valid pattern", e);
				}

				for (BlockState state : reg.getAllOf(BlockState.class)) {
					if (pattern.matcher(state.getId()).matches())
						add(state);
				}
				continue;
			}

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

			if (skipErrors) {
				Mirage.LOGGER.warn("String '" + id + "' is not a valid BlockType or BlockState");
				continue;
			}
			throw new IllegalArgumentException("String '" + id + "' is not a valid BlockType or BlockState");
		}
	}

	public void add(BlockType type) {
		this.states.addAll(type.getAllBlockStates());
	}

	public void remove(BlockType type) {
		this.states.removeAll(type.getAllBlockStates());
	}

	public void add(BlockState state) {
		this.states.add(state);
	}

	public void remove(BlockState state) {
		this.states.remove(state);
	}

	public int size() {
		return this.states.size();
	}

	public void clear() {
		this.states.clear();
	}

	public boolean contains(BlockState state) {
		return this.states.contains(state);
	}

	public void retain(BlockType type) {
		this.states.retainAll(type.getAllBlockStates());
	}

	public void add(BlockSet set) {
		this.states.addAll(set.states);
	}

	public void remove(BlockSet set) {
		this.states.removeAll(set.states);
	}

	public void retain(BlockSet set) {
		this.states.retainAll(set.states);
	}

	public BlockSet copy() {
		BlockSet set = new BlockSet();
		set.states.addAll(this.states);
		return set;
	}

	public static interface SerializationPredicate {

		boolean mode(int states, int max);

		public static SerializationPredicate limit(float f) {
			return (states, max) -> states / (float) max > f;
		}
	}
}
