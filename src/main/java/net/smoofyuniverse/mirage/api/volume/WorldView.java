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

package net.smoofyuniverse.mirage.api.volume;

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.mirage.config.world.DeobfuscationConfig;
import net.smoofyuniverse.mirage.config.world.WorldConfig;

/**
 * Represents a mutable client-side world.
 */
public interface WorldView extends BlockView, OpaqueWorld<ChunkView> {

	@Override
	default WorldView getWorld() {
		return this;
	}

	@Override
	WorldStorage getStorage();

	/**
	 * Deobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param x          The X position
	 * @param y          The Y position
	 * @param z          The Z position
	 * @param player     Use player deobf radius
	 * @param silentFail Enable or disable silent fail
	 * @throws IllegalStateException if affected chunks are not loaded and silent fail is disabled
	 */
	default void deobfuscateSurrounding(int x, int y, int z, boolean player, boolean silentFail) {
		DeobfuscationConfig.Immutable cfg = getConfig().main.deobf;
		deobfuscateSurrounding(x, y, z, player ? cfg.playerRadius : cfg.naturalRadius, silentFail);
	}

	/**
	 * @return Whether obfuscation is enabled in this world
	 */
	boolean isEnabled();

	/**
	 * Deobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param pos The position
	 * @param player Use player deobf radius
	 * @param silentFail Enable or disable silent fail
	 * @throws IllegalStateException if affected chunks are not loaded and silent fail is disabled
	 */
	default void deobfuscateSurrounding(Vector3i pos, boolean player, boolean silentFail) {
		deobfuscateSurrounding(pos.getX(), pos.getY(), pos.getZ(), player, silentFail);
	}

	/**
	 * @return The configuration of this world
	 */
	WorldConfig getConfig();

	/**
	 * Reobfuscates blocks around the given position according to the radius set in the configuration.
	 *
	 * @param pos        The position
	 * @param player     Use player reobf radius
	 * @param silentFail Enable or disable silent fail
	 * @throws IllegalStateException if affected chunks are not obfuscated and silent fail is disabled
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
	 * @throws IllegalStateException if affected chunks are not obfuscated and silent fail is disabled
	 */
	default void reobfuscateSurrounding(int x, int y, int z, boolean player, boolean silentFail) {
		DeobfuscationConfig.Immutable cfg = getConfig().main.deobf;
		reobfuscateSurrounding(x, y, z, player ? cfg.playerRadius : cfg.naturalRadius, silentFail);
	}
}
