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

package net.smoofyuniverse.mirage.util;

import org.spongepowered.api.block.BlockState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class RegistryUtil {

	public static <V> Map<BlockState, V> resolveBlockStates(Map<String, V> map) {
		Map<BlockState, V> result = new HashMap<>();
		BlockResolver resolver = new BlockResolver();

		for (Entry<String, V> e : map.entrySet()) {
			V value = e.getValue();
			resolver.resolve(e.getKey(), ((state, negate) -> {
				if (negate)
					result.remove(state);
				else
					result.put(state, value);
			}));
		}

		resolver.flushErrors();
		return result;
	}

	public static Set<BlockState> resolveBlockStates(Iterable<String> it) {
		Set<BlockState> result = new LinkedHashSet<>();
		BlockResolver resolver = new BlockResolver();

		for (String input : it) {
			resolver.resolve(input, ((state, negate) -> {
				if (negate)
					result.remove(state);
				else
					result.add(state);
			}));
		}

		resolver.flushErrors();
		return result;
	}
}
