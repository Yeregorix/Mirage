/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Objects;
import com.thomas15v.noxray.config.Options;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import com.thomas15v.noxray.modifications.internal.NetworkBlockContainer;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange.BlockUpdateData;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.Chunk;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represent a chunk viewed for the network (akka online players)
 */
public class NetworkChunk {
	private final Short2ObjectMap<BlockState> changes = new Short2ObjectOpenHashMap<>();
	private final NetworkBlockContainer[] containers;
	private final Chunk chunk;
	private final int x, z;
	private boolean obfuscated;

	public NetworkChunk(NetworkBlockContainer[] containers, Chunk chunk) {
		this.containers = containers;
		this.chunk = chunk;
		Vector3i pos = chunk.getPosition();
		this.x = pos.getX();
		this.z = pos.getZ();
	}

	public BlockState getBlock(Vector3i pos) {
		return getBlock(pos.getX(), pos.getY(), pos.getZ());
	}

	public boolean isObfuscated() {
		return this.obfuscated;
	}

	private void checkBlockBounds(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			throw new PositionOutOfBoundsException(new Vector3i(x, y, z), this.chunk.getBlockMin(), this.chunk.getBlockMax());
	}

	public boolean containsBlock(int x, int y, int z) {
		return this.chunk.containsBlock(x, y, z);
	}

	public void setBlock(Vector3i pos, BlockState block) {
		setBlock(pos.getX(), pos.getY(), pos.getZ(), block);
	}

	public void obfuscateBlocks() {
		NetworkWorld w = ((InternalWorld) this.chunk.getWorld()).getNetworkWorld();
		obfuscateBlocks(w.getModifier(), w.getOptions());
	}

	public void obfuscateBlocks(BlockModifier modifier, Options options) {
		if (this.obfuscated)
			return;

		BlockStorage storage = (BlockStorage) this.chunk.getWorld();
		Random r = ThreadLocalRandom.current();
		int x = this.chunk.getPosition().getX() * 16, z = this.chunk.getPosition().getZ() * 16;

		for (NetworkBlockContainer container : this.containers) {
			if (container == null)
				continue;

			for (int dy = 0; dy < 16; dy++) {
				int y = container.getY() + dy;
				for (int dz = 0; dz < 16; dz++) {
					for (int dx = 0; dx < 16; dx++) {
						BlockState fakeBlock = modifier.modify(storage, options, r, x + dx, y, z + dz);
						if (fakeBlock != null)
							setBlock(x + dx, y, z + dz, fakeBlock);
					}
				}
			}
		}

		this.obfuscated = true;
	}

	public void setBlock(int x, int y, int z, BlockState block) {
		checkBlockBounds(x, y, z);

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container != null)
			container.set(x & 15, y & 15, z & 15, (IBlockState) block);
	}

	public void deobfuscateBlock(int x, int y, int z) {
		if (!this.obfuscated)
			return;

		BlockState realBlock = this.chunk.getBlock(x, y, z), fakeBlock = getBlock(x, y, z);
		if (realBlock != fakeBlock) {
			setBlock(x, y, z, realBlock);
			this.changes.put((short) ((x & 15) << 12 | (z & 15) << 8 | y), realBlock);
		}
	}

	public void sendBlockChanges() {
		int size = this.changes.size();
		if (size == 0)
			return;

		if (size == 1) {
			SPacketBlockChange packet = new SPacketBlockChange();
			Entry<BlockState> e = this.changes.short2ObjectEntrySet().iterator().next();
			short pos = e.getShortKey();
			packet.blockPosition = new BlockPos((pos >> 12 & 15) + this.x * 16, pos & 255, (pos >> 8 & 15) + this.z * 16);
			packet.blockState = (IBlockState) e.getValue();
			sendPacket(packet);
		} else {
			SPacketMultiBlockChange packet = new SPacketMultiBlockChange();
			packet.chunkPos = new ChunkPos(this.x, this.z);
			BlockUpdateData[] datas = new BlockUpdateData[size];
			int i = 0;
			for (Entry<BlockState> e : this.changes.short2ObjectEntrySet())
				datas[i++] = packet.new BlockUpdateData(e.getShortKey(), (IBlockState) e.getValue());
			packet.changedBlocks = datas;
			sendPacket(packet);
		}

		this.changes.clear();
	}

	private void sendPacket(Packet<?> packet) {
		PlayerChunkMapEntry entry = ((WorldServer) this.chunk.getWorld()).getPlayerChunkMap().getEntry(this.x, this.z);
		if (entry != null)
			entry.sendPacket(packet);
	}

	@Nullable
	public BlockState getBlock(int x, int y, int z) {
		checkBlockBounds(x, y, z);

		NetworkBlockContainer container = this.containers[y >> 4];
		return container == null ? null : (BlockState) container.get(x & 15, y & 15, z & 15);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.containers, this.chunk);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		NetworkChunk that = (NetworkChunk) o;
		return Objects.equal(this.containers, that.containers) && Objects.equal(this.chunk, that.chunk);
	}

	public Chunk getChunk() {
		return this.chunk;
	}
}
