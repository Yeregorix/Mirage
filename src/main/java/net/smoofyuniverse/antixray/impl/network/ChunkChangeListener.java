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

package net.smoofyuniverse.antixray.impl.network;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange.BlockUpdateData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.antixray.AntiXrayTimings;
import org.spongepowered.api.block.BlockState;

public class ChunkChangeListener {
	private Short2ObjectMap<IBlockState> changesMap = new Short2ObjectOpenHashMap<>(64);
	private int changedSections, changes;

	private PlayerChunkMap playerChunkMap;
	private Chunk chunk;

	public ChunkChangeListener(Chunk chunk) {
		this.chunk = chunk;
		this.playerChunkMap = ((WorldServer) chunk.getWorld()).getPlayerChunkMap();
	}

	public void addChange(int x, int y, int z, BlockState newBlock) {
		this.changes++;
		if (this.changes == 64)
			this.changesMap.clear();
		if (this.changes < 64)
			this.changesMap.put((short) (x << 12 | z << 8 | y), (IBlockState) newBlock);
		this.changedSections |= 1 << (y >> 4);
	}

	public void sendChanges() {
		if (this.changes == 0)
			return;

		PlayerChunkMapEntry entry = getEntry();
		if (entry != null && entry.isSentToPlayers()) {
			AntiXrayTimings.SENDING_CHANGES.startTiming();

			if (this.changes == 1) {
				SPacketBlockChange packet = new SPacketBlockChange();
				Entry<IBlockState> e = this.changesMap.short2ObjectEntrySet().iterator().next();
				short pos = e.getShortKey();

				packet.blockPosition = new BlockPos((pos >> 12 & 15) + this.chunk.x * 16, pos & 255, (pos >> 8 & 15) + this.chunk.z * 16);
				packet.blockState = e.getValue();
				entry.sendPacket(packet);
			} else if (this.changes < 64) {
				SPacketMultiBlockChange packet = new SPacketMultiBlockChange();
				packet.chunkPos = chunk.getPos();
				BlockUpdateData[] datas = new BlockUpdateData[this.changes];
				int i = 0;
				for (Entry<IBlockState> e : this.changesMap.short2ObjectEntrySet())
					datas[i++] = packet.new BlockUpdateData(e.getShortKey(), e.getValue());
				packet.changedBlocks = datas;
				entry.sendPacket(packet);
			} else {
				entry.sendPacket(new SPacketChunkData(this.chunk, this.changedSections));
			}

			AntiXrayTimings.SENDING_CHANGES.stopTiming();
		}

		clearChanges();
	}

	private PlayerChunkMapEntry getEntry() {
		return this.playerChunkMap.getEntry(this.chunk.x, this.chunk.z);
	}

	public void clearChanges() {
		this.changes = 0;
		this.changesMap.clear();
		this.changedSections = 0;
	}

	public boolean isChunkSent() {
		PlayerChunkMapEntry entry = getEntry();
		return entry != null && entry.isSentToPlayers();
	}
}
