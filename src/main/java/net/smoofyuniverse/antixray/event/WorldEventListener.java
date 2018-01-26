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

package net.smoofyuniverse.antixray.event;

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.config.DeobfuscationConfig;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.event.world.SaveWorldEvent;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;

public class WorldEventListener {

	@Listener(order = Order.POST)
	public void onWorldLoad(LoadWorldEvent e) {
		try {
			((InternalWorld) e.getTargetWorld()).getView().loadConfig();
		} catch (Exception ex) {
			AntiXray.LOGGER.error("Failed to load world " + e.getTargetWorld().getName() + "'s configuration", ex);
		}
	}

	@Listener
	public void onWorldSave(SaveWorldEvent.Pre e) {
		for (Chunk c : e.getTargetWorld().getLoadedChunks()) {
			NetworkChunk netChunk = ((InternalChunk) c).getView();
			if (netChunk != null)
				netChunk.saveToCacheLater();
		}
	}

	@Listener(order = Order.POST)
	public void onBlockChange(ChangeBlockEvent e) {
		boolean player = e.getCause().containsType(Player.class);
		for (Transaction<BlockSnapshot> t : e.getTransactions()) {
			if (t.isValid())
				t.getOriginal().getLocation().ifPresent(loc -> updateSurroundingBlocks(loc, player));
		}
	}

	public static void updateSurroundingBlocks(Location<World> loc, boolean player) {
		updateSurroundingBlocks(loc.getExtent(), loc.getBlockPosition(), player);
	}

	public static void updateSurroundingBlocks(World w, Vector3i pos, boolean player) {
		updateSurroundingBlocks(w, pos.getX(), pos.getY(), pos.getZ(), player);
	}

	public static void updateBlock(Location<World> loc) {
		updateBlock(loc.getExtent(), loc.getBlockPosition());
	}

	public static void updateBlock(World w, Vector3i pos) {
		updateBlock(w, pos.getX(), pos.getY(), pos.getZ());
	}

	public static void updateSurroundingBlocks(World w, int x, int y, int z, boolean player) {
		DeobfuscationConfig.Immutable cfg = ((InternalWorld) w).getView().getConfig().deobf;
		int r = player ? cfg.playerRadius : cfg.naturalRadius;

		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++)
					updateBlock(w, x + dx, y + dy, z + dz);
			}
		}
	}

	@Listener(order = Order.POST)
	public void onBlockInteract(InteractBlockEvent e) {
		if (e.getCause().containsType(Player.class))
			e.getTargetBlock().getLocation().ifPresent(loc -> updateSurroundingBlocks(loc, true));
	}

	@Listener(order = Order.POST)
	public void onExplosionDetonate(ExplosionEvent.Detonate e) {
		boolean player = e.getCause().containsType(Player.class);
		List<Location<World>> list = e.getAffectedLocations();
		for (Location<World> loc : list) {
			DeobfuscationConfig.Immutable cfg = ((InternalWorld) loc.getExtent()).getView().getConfig().deobf;
			int r = player ? cfg.playerRadius : cfg.naturalRadius;

			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx == 0 && dy == 0 && dz == 0)
							continue;

						Location<World> loc2 = loc.add(dx, dy, dz);
						if (!list.contains(loc2))
							updateBlock(loc2);
					}
				}
			}
		}
	}

	public static void updateBlock(World w, int x, int y, int z) {
		if (y >= 0 && y < 256)
			((InternalWorld) w).getView().deobfuscate(x, y, z);
	}
}
