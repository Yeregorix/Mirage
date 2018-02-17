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
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.NetworkChunk;
import net.smoofyuniverse.antixray.impl.network.NetworkWorld;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.filter.type.Exclude;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.event.world.SaveWorldEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.Set;

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
		((InternalWorld) e.getTargetWorld()).getView().getLoadedChunkViews().forEach(NetworkChunk::saveToCacheLater);
	}

	@Exclude(ChangeBlockEvent.Post.class)
	@Listener(order = Order.POST)
	public void onBlockChange(ChangeBlockEvent e) {
		boolean player = e.getCause().containsType(Player.class);

		if (e instanceof ChangeBlockEvent.Place) {
			for (Transaction<BlockSnapshot> t : e.getTransactions()) {
				if (t.isValid())
					t.getOriginal().getLocation().ifPresent(loc -> reobfuscateSurrounding(loc, player));
			}
		} else {
			for (Transaction<BlockSnapshot> t : e.getTransactions()) {
				if (t.isValid())
					t.getOriginal().getLocation().ifPresent(loc -> deobfuscateSurrounding(loc, player));
			}
		}
	}

	@Listener(order = Order.POST)
	public void onExplosionDetonate(ExplosionEvent.Detonate e) {
		Set<Vector3i> blocks = new HashSet<>();

		for (Location<World> loc : e.getAffectedLocations()) {
			Vector3i pos = loc.getBlockPosition();
			blocks.add(pos.add(1, 0, 0));
			blocks.add(pos.add(-1, 0, 0));
			blocks.add(pos.add(0, 1, 0));
			blocks.add(pos.add(0, -1, 0));
			blocks.add(pos.add(0, 0, 1));
			blocks.add(pos.add(0, 0, -1));
		}

		for (Location<World> loc : e.getAffectedLocations())
			blocks.remove(loc.getBlockPosition());

		NetworkWorld netWorld = ((InternalWorld) e.getTargetWorld()).getView();
		for (Vector3i pos : blocks)
			netWorld.deobfuscate(pos);
	}

	private static void reobfuscateSurrounding(Location<World> loc, boolean player) {
		((InternalWorld) loc.getExtent()).getView().reobfuscateSurrounding(loc.getBlockPosition(), player);
	}

	private static void deobfuscateSurrounding(Location<World> loc, boolean player) {
		((InternalWorld) loc.getExtent()).getView().deobfuscateSurrounding(loc.getBlockPosition(), player);
	}
}
