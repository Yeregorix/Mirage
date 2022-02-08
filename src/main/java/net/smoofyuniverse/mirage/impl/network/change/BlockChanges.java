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
import org.spongepowered.math.vector.Vector3i;

import java.util.function.Consumer;

public class BlockChanges {
	private final Short2ObjectMap<BlockState> blocks = new Short2ObjectOpenHashMap<>();
	private final LevelChunk chunk;
	private final SectionPos pos;

	public BlockChanges(LevelChunk chunk, int y) {
		this.chunk = chunk;
		this.pos = SectionPos.of(chunk.getPos(), y);
	}

	public void add(Vector3i pos, BlockState state) {
		add(pos.x(), pos.y(), pos.z(), state);
	}

	public void add(int x, int y, int z, BlockState state) {
		add((short) ((x & 15) << 8 | (z & 15) << 4 | (y & 15)), state);
	}

	public void add(short index, BlockState state) {
		this.blocks.put(index, state);
	}

	public void sendTo(ServerPlayer player) {
		sendTo(player.connection::send);
	}

	public void sendTo(Consumer<Packet<?>> consumer) {
		int changes = this.blocks.size();
		if (changes == 0)
			return;

		int minX = this.pos.minBlockX(), minY = this.pos.minBlockY(), minZ = this.pos.minBlockZ();

		if (changes == 1) {
			Entry<BlockState> e = this.blocks.short2ObjectEntrySet().iterator().next();
			short key = e.getShortKey();
			BlockPos pos = new BlockPos(minX + (key >> 8 & 15), minY + (key & 15), minZ + (key >> 4 & 15));
			BlockState state = e.getValue();

			consumer.accept(new ClientboundBlockUpdatePacket(pos, state));

			ClientboundBlockEntityDataPacket p2 = getEntityPacket(pos, state);
			if (p2 != null)
				consumer.accept(p2);
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

			consumer.accept(p);

			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (i = 0; i < changes; i++) {
				short key = p.positions[i];
				pos.set(minX + (key >> 8 & 15), minY + (key & 15), minZ + (key >> 4 & 15));

				ClientboundBlockEntityDataPacket p2 = getEntityPacket(pos, p.states[i]);
				if (p2 != null)
					consumer.accept(p2);
			}
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
