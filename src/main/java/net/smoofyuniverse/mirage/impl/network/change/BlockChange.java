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

package net.smoofyuniverse.mirage.impl.network.change;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BlockChange {
	private final Packet<?>[] packets;

	private BlockChange(Packet<?>... packets) {
		this.packets = packets;
	}

	private BlockChange() {
		this.packets = null;
	}

	public void sendTo(Player player) {
		if (this.packets != null)
			send(player);
	}

	private void send(Player player) {
		for (Packet<?> packet : this.packets)
			((ServerPlayer) player).connection.send(packet);
	}

	public void sendTo(Iterable<Player> players) {
		if (this.packets != null) {
			for (Player p : players)
				send(p);
		}
	}

	public void sendTo(Stream<Player> players) {
		if (this.packets != null)
			players.forEach(this::send);
	}

	public static Builder builder(LevelChunk chunk, int y) {
		return new Builder(chunk, y);
	}

	public static class Builder {
		private final Short2ObjectMap<BlockState> blocks = new Short2ObjectOpenHashMap<>();
		private final LevelChunk chunk;
		private final SectionPos pos;

		private Builder(LevelChunk chunk, int y) {
			this.chunk = chunk;
			this.pos = SectionPos.of(chunk.getPos(), y);
		}

		public void add(Vector3i pos, org.spongepowered.api.block.BlockState state) {
			add(pos.x(), pos.y(), pos.z(), state);
		}

		public void add(int x, int y, int z, org.spongepowered.api.block.BlockState state) {
			add((short) ((x & 15) << 8 | (z & 15) << 4 | (y & 15)), state);
		}

		public void add(short index, org.spongepowered.api.block.BlockState state) {
			this.blocks.put(index, (BlockState) state);
		}

		public BlockChange build() {
			return build(true);
		}

		private BlockChange build(boolean includeBlockEntities) {
			int changes = this.blocks.size();

			if (changes == 0)
				return new BlockChange();

			int minX = this.pos.minBlockX() << 4, minZ = this.pos.minBlockZ();

			if (changes == 1) {
				Entry<BlockState> e = this.blocks.short2ObjectEntrySet().iterator().next();
				short key = e.getShortKey();
				BlockPos pos = new BlockPos(minX + (key >> 8 & 15), key & 15, minZ + (key >> 4 & 15));
				BlockState state = e.getValue();

				ClientboundBlockUpdatePacket p = new ClientboundBlockUpdatePacket(pos, state);

				if (includeBlockEntities) {
					ClientboundBlockEntityDataPacket p2 = getEntityPacket(pos, state);
					if (p2 != null)
						return new BlockChange(p, p2);
				}

				return new BlockChange(p);
			} else {
				ClientboundSectionBlocksUpdatePacket p = new ClientboundSectionBlocksUpdatePacket();
				p.sectionPos = this.pos;
				p.positions = new short[changes];
				p.states = new BlockState[changes];
				p.suppressLightUpdates = true;

				int i = 0;
				for (Entry<BlockState> e : this.blocks.short2ObjectEntrySet()) {
					p.positions[i] = e.getShortKey();
					p.states[i++] = e.getValue();
				}

				if (includeBlockEntities) {
					List<Packet<?>> packets = new ArrayList<>();
					packets.add(p);

					BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
					for (i = 0; i < changes; i++) {
						short key = p.positions[i];
						pos.set(minX + (key >> 8 & 15), key & 15, minZ + (key >> 4 & 15));

						ClientboundBlockEntityDataPacket p2 = getEntityPacket(pos, p.states[i]);
						if (p2 != null)
							packets.add(p2);
					}

					return new BlockChange(packets.toArray(new Packet<?>[0]));
				}

				return new BlockChange(p);
			}
		}

		private ClientboundBlockEntityDataPacket getEntityPacket(BlockPos pos, BlockState state) {
			if (state.getBlock().isEntityBlock()) {
				BlockEntity entity = this.chunk.getBlockEntity(pos);
				if (entity != null) {
					ClientboundBlockEntityDataPacket packet = entity.getUpdatePacket();
					if (packet != null)
						return packet;
				}
			}
			return null;
		}
	}
}
