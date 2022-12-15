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

package net.smoofyuniverse.mirage.api.modifier;

import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.registry.DefaultedRegistryType;
import org.spongepowered.api.registry.RegistryRoots;
import org.spongepowered.api.registry.RegistryType;
import org.spongepowered.api.util.annotation.CatalogedBy;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.api.world.server.storage.ServerWorldProperties;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.math.vector.Vector3i;

import java.util.Random;

/**
 * This object is used to modify chunk per chunk the view of the world sent to players.
 */
@CatalogedBy(ChunkModifiers.class)
public interface ChunkModifier {
	ResourceKey REGISTRY_TYPE_KEY = Mirage.key("chunk_modifier");
	DefaultedRegistryType<ChunkModifier> REGISTRY_TYPE = RegistryType.of(RegistryRoots.SPONGE, REGISTRY_TYPE_KEY).asDefaultedType(Sponge::game);

	/**
	 * @return false if this modifier is lightweight and cache is useless, true otherwise
	 */
	default boolean requireCache() {
		return true;
	}

	/**
	 * @param properties The world properties
	 * @return true if this modifier is compatible with the world properties
	 */
	default boolean isCompatible(ServerWorldProperties properties) {
		return true;
	}

	/**
	 * Generates a configuration from the given node.
	 *
	 * @param node      The configuration node (mutable)
	 * @param worldType The world type defined in the main configuration, might differ from the actual world type
	 * @param preset    An optional preset
	 * @return The configuration
	 */
	Object loadConfiguration(ConfigurationNode node, WorldType worldType, String preset) throws SerializationException;

	/**
	 * Generates a cache signature to make a summary of all elements that may impact the aspect of the modified chunk.
	 * A different signature from the cached one will cause the cached object to be invalidated.
	 *
	 * @param builder The signature builder
	 * @param config  The configuration
	 */
	void appendSignature(Signature.Builder builder, Object config);

	/**
	 * Checks whether this modifier requires that the neighbor chunks are loaded.
	 *
	 * @return true if this modifier requires that the neighbor chunks are loaded.
	 */
	default boolean requireNeighborsLoaded() {
		return true;
	}

	/**
	 * Modifies the ChunkView that will be sent to players.
	 * This method might check and modify thousands blocks and thus must be optimized to be as fast as possible.
	 *
	 * @param view   The ChunkView to modify
	 * @param r      The Random object that should be used by the modifier
	 * @param config The configuration
	 */
	default void modify(ChunkView view, Random r, Object config) {
		modify(view, view.min(), view.max(), r, config);
	}

	/**
	 * Modifies the BlockView that will be sent to players.
	 * This method might check and modify thousands blocks and thus must be optimized to be as fast as possible.
	 *
	 * @param view The BlockView to modify
	 * @param min  The lowest block location to modify
	 * @param max  The highest block location to modify
	 * @param r    The Random object that should be used by the modifier
	 * @param config The configuration
	 */
	void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config);
}
