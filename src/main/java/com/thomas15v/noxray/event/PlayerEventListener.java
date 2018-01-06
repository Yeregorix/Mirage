/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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

package com.thomas15v.noxray.event;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;
import java.util.Set;

public class PlayerEventListener {
	private static final Set<BlockType> whitelist = ImmutableSet.of(BlockTypes.AIR, BlockTypes.PISTON, BlockTypes.PISTON_EXTENSION, BlockTypes.PISTON_HEAD, BlockTypes.STICKY_PISTON);
	private static final int[] offsets = {-1, 0, 1};

	@Listener(order = Order.POST)
	public void onBlockBreak(ChangeBlockEvent.Break e) {
		for (Transaction<BlockSnapshot> t : e.getTransactions()) {
			if (t.isValid() && !whitelist.contains(t.getFinal().getState().getType()))
				t.getOriginal().getLocation().ifPresent(PlayerEventListener::updateSurroundingBlocks);
		}
	}

	@Listener(order = Order.POST)
	public void onBlockInteract(InteractBlockEvent e) {
		if (e.getCause().containsType(Player.class))
			e.getTargetBlock().getLocation().ifPresent(PlayerEventListener::updateSurroundingBlocks);
	}

	@Listener(order = Order.POST)
	public void onExplosionDetonate(ExplosionEvent.Detonate e) {
		List<Location<World>> list = e.getAffectedLocations();
		for (Location<World> loc : list) {
			for (int dx : offsets) {
				for (int dy : offsets) {
					for (int dz : offsets) {
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

	public static void updateBlock(Location<World> loc) {
		updateBlock(loc.getExtent(), loc.getBlockPosition());
	}

	public static void updateBlock(World w, Vector3i pos) {
		updateBlock(w, pos.getX(), pos.getY(), pos.getZ());
	}

	public static void updateBlock(World w, int x, int y, int z) {
		if (w.getBlockType(x, y, z) != BlockTypes.AIR)
			w.resetBlockChange(x, y, z);
	}

	public static void updateSurroundingBlocks(Location<World> loc) {
		updateSurroundingBlocks(loc.getExtent(), loc.getBlockPosition());
	}

	public static void updateSurroundingBlocks(World w, Vector3i pos) {
		updateSurroundingBlocks(w, pos.getX(), pos.getY(), pos.getZ());
	}

	public static void updateSurroundingBlocks(World w, int x, int y, int z) {
		for (int dx : offsets) {
			for (int dy : offsets) {
				for (int dz : offsets) {
					if (dx == 0 && dy == 0 && dz == 0)
						continue;

					updateBlock(w, x + dx, y + dy, z + dz);
				}
			}
		}
	}
}
