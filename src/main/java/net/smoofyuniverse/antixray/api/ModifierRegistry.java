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

package net.smoofyuniverse.antixray.api;

import net.smoofyuniverse.antixray.modifier.EmptyModifier;
import net.smoofyuniverse.antixray.modifier.HideAllModifier;
import net.smoofyuniverse.antixray.modifier.ObviousModifier;
import net.smoofyuniverse.antixray.modifier.RandomModifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A registry used to map modifiers by name
 */
public class ModifierRegistry {
	private static final Map<String, ViewModifier> modifiers = new HashMap<>();

	/**
	 * Registers a new modifier in this registry.
	 * @param modifier The ViewModifier
	 */
	public static void register(ViewModifier modifier) {
		if (contains(modifier.getName()))
			throw new IllegalArgumentException("Name is already registered");
		modifiers.put(modifier.getName().toLowerCase(), modifier);
	}

	/**
	 * @param name The name of the modifier
	 * @return Whether this registry contains a modifier with the specified name
	 */
	public static boolean contains(String name) {
		return modifiers.containsKey(name.toLowerCase());
	}

	/**
	 * @param name The name of the modifier
	 * @return The modifier registered in this registry with the specified name
	 */
	public static Optional<ViewModifier> get(String name) {
		return Optional.ofNullable(modifiers.get(name.toLowerCase()));
	}

	static {
		register(ObviousModifier.INSTANCE);
		register(RandomModifier.INSTANCE);
		register(HideAllModifier.INSTANCE);
		register(EmptyModifier.INSTANCE);
	}
}