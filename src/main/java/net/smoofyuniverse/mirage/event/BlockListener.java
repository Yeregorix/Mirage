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

package net.smoofyuniverse.mirage.event;

import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import net.smoofyuniverse.mirage.util.BlockUtil;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.transaction.BlockTransaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;

import java.util.HashSet;
import java.util.Set;

public class BlockListener {

	@Listener(order = Order.POST)
	public void onBlockChange(ChangeBlockEvent.All e) {
		if (e.cause().containsType(Explosion.class))
			return;

		boolean player = e.cause().containsType(Player.class);

		for (BlockTransaction t : e.transactions()) {
			if (!t.isValid())
				continue;

			BlockSnapshot original = t.original();
			boolean wasOpaque = BlockUtil.isOpaque(original.state()), isOpaque = BlockUtil.isOpaque(t.finalReplacement().state());
			if (wasOpaque == isOpaque)
				continue;

			ServerLocation loc = original.location().orElse(null);
			if (loc == null)
				continue;

			NetworkWorld world = ((InternalWorld) loc.world()).view();
			Vector3i pos = loc.blockPosition();

			if (isOpaque)
				world.reobfuscateSurrounding(pos, player, true);
			else
				world.deobfuscateSurrounding(pos, player, true);
		}
	}

	@Listener(order = Order.POST)
	public void onExplosionDetonate(ExplosionEvent.Detonate e) {
		Set<Vector3i> blocks = new HashSet<>();

		for (ServerLocation loc : e.affectedLocations()) {
			if (BlockUtil.isOpaque(loc.block())) {
				Vector3i pos = loc.blockPosition();
				blocks.add(pos.add(1, 0, 0));
				blocks.add(pos.add(-1, 0, 0));
				blocks.add(pos.add(0, 1, 0));
				blocks.add(pos.add(0, -1, 0));
				blocks.add(pos.add(0, 0, 1));
				blocks.add(pos.add(0, 0, -1));
			}
		}

		for (ServerLocation loc : e.affectedLocations())
			blocks.remove(loc.blockPosition());

		NetworkWorld world = ((InternalWorld) e.world()).view();
		for (Vector3i pos : blocks)
			world.deobfuscate(pos);
	}
}
