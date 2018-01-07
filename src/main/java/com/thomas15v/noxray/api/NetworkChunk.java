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
import com.thomas15v.noxray.modifications.internal.NetworkBlockContainer;
import net.minecraft.block.state.IBlockState;
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
	private NetworkBlockContainer[] containers;
	private Chunk chunk;
	private boolean obfuscated;

	public NetworkChunk(NetworkBlockContainer[] containers, Chunk chunk) {
		this.containers = containers;
		this.chunk = chunk;
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

	/**
	 * Obfuscates all the known blocks inside a chunk. Since we don't know the blocks bordering the chunk yet
	 */
	public void obfuscateBlocks(BlockModifier modifier) {
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
						BlockState fakeBlock = modifier.modify(storage, r, x + dx, y, z + dz);
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
			this.chunk.getWorld().sendBlockChange(x, y, z, realBlock);
		}
	}

	@Nullable
	public BlockState getBlock(int x, int y, int z) {
		checkBlockBounds(x, y, z);

		NetworkBlockContainer container = this.containers[y >> 4];
		return container == null ? null : (BlockState) container.get(x & 15, y & 15, z & 15);
	}

	public Vector3i getPosition() {
		return this.chunk.getPosition();
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
