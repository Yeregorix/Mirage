/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.noxray.modifier;

import net.smoofyuniverse.noxray.api.BlockModifier;

import java.util.HashMap;
import java.util.Map;

public class ModifierRegistry {
	private static final Map<String, BlockModifier> modifiers = new HashMap<>();

	public static void register(String name, BlockModifier modifier) {
		if (contains(name))
			throw new IllegalArgumentException("Modifier already registered");
		modifiers.put(name.toLowerCase(), modifier);
	}

	public static boolean contains(String name) {
		return modifiers.containsKey(name.toLowerCase());
	}

	public static BlockModifier get(String name) {
		return modifiers.get(name.toLowerCase());
	}

	static {
		register("obvious", new ObviousModifier());
		register("random", new RandomModifier());
		register("hideall", BlockModifier.HIDE_ALL);
		register("empty", BlockModifier.EMPTY);
	}
}