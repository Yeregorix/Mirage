/*
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

package net.smoofyuniverse.antixray.api.volume;

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.antixray.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.antixray.config.world.DeobfuscationConfig;
import net.smoofyuniverse.antixray.config.world.WorldConfig;
import org.spongepowered.api.util.Identifiable;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Represents a mutable client-side world.
 */
public interface WorldView extends BlockView, Identifiable {

	/**
	 * @return The WorldStorage which is associated with this WorldView
	 */
	@Override
	WorldStorage getStorage();

	/**
	 * @return The name of this world
	 */
	String getName();

	/**
	 * @return The properties of this world
	 */
	WorldProperties getProperties();

	/**
	 * @return The modifiers applied to this world and their configurations
	 */
	List<ConfiguredModifier> getModifiers();

	/**
	 * @return The configuration of this world
	 */
	WorldConfig.Immutable getConfig();

	/**
	 * @return Whether obfuscation is enabled in this world
	 */
	boolean isEnabled();

	/**
	 * @param pos The position
	 * @return Whether the ChunkView at the given <strong>chunk</strong> position is loaded
	 */
	default boolean isChunkLoaded(Vector3i pos) {
		return isChunkLoaded(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return Whether the ChunkView at the given <strong>chunk</strong> position is loaded
	 */
	default boolean isChunkLoaded(int x, int y, int z) {
		return getChunkView(x, y, z).isPresent();
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return The ChunkView at the given <strong>chunk</strong> position
	 */
	Optional<ChunkView> getChunkView(int x, int y, int z);

	/**
	 * @param pos The position
	 * @return The ChunkView at the given <strong>chunk</strong> position
	 */
	default Optional<ChunkView> getChunkView(Vector3i pos) {
		return getChunkView(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * @param pos The position
	 * @return The ChunkView at the given <strong>block</strong> position
	 */
	default Optional<ChunkView> getChunkViewAt(Vector3i pos) {
		return getChunkViewAt(pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @return The ChunkView at the given <strong>block</strong> position
	 */
	Optional<ChunkView> getChunkViewAt(int x, int y, int z);

	/**
	 * @return A collection of all loaded ChunkViews in this world
	 */
	Collection<? extends ChunkView> getLoadedChunkViews();

	/**
	 * Deobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param pos The position
	 * @param player Use player deobf radius
	 */
	default void deobfuscateSurrounding(Vector3i pos, boolean player) {
		deobfuscateSurrounding(pos.getX(), pos.getY(), pos.getZ(), player);
	}

	/**
	 * Deobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @param player Use player deobf radius
	 */
	default void deobfuscateSurrounding(int x, int y, int z, boolean player) {
		DeobfuscationConfig.Immutable cfg = getConfig().deobf;
		deobfuscateSurrounding(x, y, z, player ? cfg.playerRadius : cfg.naturalRadius);
	}

	/**
	 * Reobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param pos The position
	 * @param player Use player reobf radius
	 * @param silentFail Enable or disable silent fail
	 * @throws IllegalStateException if affected chunks are not fully obfuscated and silent fail is disabled
	 */
	default void reobfuscateSurrounding(Vector3i pos, boolean player, boolean silentFail) {
		reobfuscateSurrounding(pos.getX(), pos.getY(), pos.getZ(), player, silentFail);
	}

	/**
	 * Reobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param x The X position
	 * @param y The Y position
	 * @param z The Z position
	 * @param player Use player reobf radius
	 * @param silentFail Enable or disable silent fail
	 * @throws IllegalStateException if affected chunks are not fully obfuscated and silent fail is disabled
	 */
	default void reobfuscateSurrounding(int x, int y, int z, boolean player, boolean silentFail) {
		DeobfuscationConfig.Immutable cfg = getConfig().deobf;
		reobfuscateSurrounding(x, y, z, player ? cfg.playerRadius : cfg.naturalRadius, silentFail);
	}
}
