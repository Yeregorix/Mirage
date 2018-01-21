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

package net.smoofyuniverse.antixray.api.modifier;

import com.google.common.collect.ImmutableList;
import org.spongepowered.api.registry.AdditionalCatalogRegistryModule;
import org.spongepowered.api.registry.RegistrationPhase;
import org.spongepowered.api.registry.util.DelayedRegistration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChunkModifierRegistryModule implements AdditionalCatalogRegistryModule<ChunkModifier> {
	private static final ChunkModifierRegistryModule INSTANCE = new ChunkModifierRegistryModule();

	private final Map<String, ChunkModifier> modifiers = new HashMap<>();

	private ChunkModifierRegistryModule() {}

	@Override
	public Optional<ChunkModifier> getById(String id) {
		id = id.toLowerCase();
		return Optional.ofNullable(this.modifiers.get(id.indexOf(':') == -1 ? ("antixray:" + id) : id));
	}

	@Override
	public Collection<ChunkModifier> getAll() {
		return ImmutableList.copyOf(this.modifiers.values());
	}

	@DelayedRegistration(RegistrationPhase.PRE_INIT)
	@Override
	public void registerDefaults() {
		register(ChunkModifiers.EMPTY);
		register(ChunkModifiers.HIDEALL);
		register(ChunkModifiers.OBVIOUS);
		register(ChunkModifiers.RANDOM);
	}

	private void register(ChunkModifier modifier) {
		this.modifiers.put(modifier.getId(), modifier);
	}

	@Override
	public void registerAdditionalCatalog(ChunkModifier modifier) {
		if (this.modifiers.containsKey(modifier.getId()))
			throw new IllegalArgumentException("Cannot register an already registered ChunkModifier: " + modifier.getId());
		register(modifier);
	}

	public static ChunkModifierRegistryModule get() {
		return INSTANCE;
	}
}
