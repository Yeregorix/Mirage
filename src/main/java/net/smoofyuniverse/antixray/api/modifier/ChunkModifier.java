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

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.antixray.api.cache.Signature;
import net.smoofyuniverse.antixray.api.volume.BlockView;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.util.annotation.CatalogedBy;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Random;

/**
 * This object is used to modify chunk per chunk the view of the world sent to players.
 */
@CatalogedBy(ChunkModifiers.class)
public abstract class ChunkModifier implements CatalogType {
	private final Timing timing;
	private final String id, name;

	/**
	 * @param plugin The plugin owning this modifier
	 * @param name   The name of this modifier
	 */
	public ChunkModifier(Object plugin, String name) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("Name");
		PluginContainer container = Sponge.getGame().getPluginManager().fromInstance(plugin).orElseThrow(() -> new IllegalArgumentException("Provided object is not a plugin instance"));
		this.timing = Timings.of(container, "Modifier: " + name);
		this.id = container.getId() + ":" + name.toLowerCase();
		this.name = name;
	}

	@Override
	public final String getId() {
		return this.id;
	}

	@Override
	public final String getName() {
		return this.name;
	}

	/**
	 * @return A Timing that will be used to monitor performances of this modifier
	 */
	public final Timing getTiming() {
		return this.timing;
	}

	/**
	 * @return false if this modifier is very light and caching is useless, true otherwise
	 */
	public boolean shouldCache() {
		return true;
	}

	/**
	 * @param world The world
	 * @return true if this modifier is compatible with this world
	 */
	public boolean isCompatible(WorldProperties world) {
		return true;
	}

	/**
	 * Generates a config object from the given node.
	 *
	 * @param node  The configuration node (mutable)
	 * @param world The world where this config object will be applicable
	 * @return Your config object
	 */
	public abstract Object loadConfiguration(ConfigurationNode node, WorldProperties world) throws ObjectMappingException;

	/**
	 * Generates a cache signature to make a summary of all elements that may impact the aspect of the modified chunk.
	 * A different signature from the cached one will cause the cached object to be invalidated.
	 * @param builder The signature builder
	 * @param config Your config object
	 */
	public abstract void appendSignature(Signature.Builder builder, Object config);

	/**
	 * A fast method to check whether this modifier is ready to modify a chunk.
	 * Some modifiers might need to check whether neighboring chunks are loaded.
	 * @param view The ChunkView to modify
	 * @return true if this modifier is ready to modify the chunk
	 */
	public abstract boolean isReady(ChunkView view, Object config);

	/**
	 * Modifies the ChunkView that will be send to players.
	 * This method might check and modify thousands blocks and thus must optimized to be as fast as possible.
	 * @param view The ChunkView to modify
	 * @param r The Random object that should be used by the modifier
	 */
	public void modify(ChunkView view, Random r, Object config) {
		modify(view, view.getBlockMin(), view.getMutableMax(), r, config);
	}

	/**
	 * Modifies the BlockView that will be send to players.
	 * This method might check and modify thousands blocks and thus must optimized to be as fast as possible.
	 *
	 * @param view The BlockView to modify
	 * @param min  The lowest block location to modify
	 * @param max  The highest block location to modify
	 * @param r    The Random object that should be used by the modifier
	 */
	public abstract void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config);
}
