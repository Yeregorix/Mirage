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

package net.smoofyuniverse.mirage.impl.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.smoofyuniverse.mirage.impl.internal.InternalPalettedContainer;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;

import java.util.Arrays;

import static net.minecraft.world.level.chunk.LevelChunkSection.GLOBAL_BLOCKSTATE_PALETTE;

public class NetworkSection {
	private final LevelChunkSection section;
	private final int minY;

	private final PalettedContainer<BlockState> states;
	private DataLayer dynamism;

	private final int[] dynCount = new int[16];
	private int nonAirBlocks;

	boolean dirty = false;

	public NetworkSection(LevelChunkSection section) {
		this.section = section;
		this.minY = section.bottomBlockY();

		this.states = new PalettedContainer<>(GLOBAL_BLOCKSTATE_PALETTE, Block.BLOCK_STATE_REGISTRY, NbtUtils::readBlockState, NbtUtils::writeBlockState, Blocks.AIR.defaultBlockState());
		this.dynamism = new DataLayer();
		this.dynCount[0] = 4096;

		((InternalPalettedContainer) section.getStates()).setOnRead(this::readStates);
	}

	public void readStates(ListTag palette, long[] states) {
		this.states.read(palette, states);
		recalculateAirBlocks();
		this.dirty = true;
	}

	private void recalculateAirBlocks() {
		this.nonAirBlocks = 0;
		this.states.count((state, count) -> {
			if (!state.isAir())
				this.nonAirBlocks += count;
		});
	}

	public InternalSection getStorage() {
		return (InternalSection) this.section;
	}

	public int getBlockMinY() {
		return this.minY;
	}

	public int getY() {
		return this.minY >> 4;
	}

	public boolean isEmpty() {
		return this.nonAirBlocks == 0;
	}

	public void deobfuscate(ChunkChangeListener listener) {
		acquire();
		this.nonAirBlocks = 0;

		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					BlockState fakeState = getBlockState(x, y, z), realState = this.section.getBlockState(x, y, z);

					if (!realState.isAir())
						this.nonAirBlocks++;

					if (fakeState != realState) {
						this.states.getAndSetUnchecked(x, y, z, realState);

						if (listener != null)
							listener.addChange(x, this.minY + y, z);
					}
				}
			}
		}

		this.dirty = true;
		release();
	}

	public void acquire() {
		this.states.acquire();
	}

	public BlockState getBlockState(int x, int y, int z) {
		return this.states.get(x, y, z);
	}

	public void release() {
		this.states.release();
	}

	public boolean deobfuscate(ChunkChangeListener listener, int x, int y, int z) {
		if (setBlockState(x, y, z, this.section.getBlockState(x, y, z))) {
			setDynamism(x, y, z, 0);
			if (listener != null) {
				listener.updateDynamism(x, this.minY + y, z, 0);
				listener.addChange(x, this.minY + y, z);
			}
			return true;
		}
		return false;
	}

	public boolean setBlockState(int x, int y, int z, BlockState state) {
		BlockState oldState = this.states.getAndSetUnchecked(x, y, z, state);
		if (oldState == state)
			return false;

		if (!oldState.isAir())
			this.nonAirBlocks--;
		if (!state.isAir())
			this.nonAirBlocks++;

		this.dirty = true;
		return true;
	}

	public int getMaxDynamism() {
		for (int i = 10; i >= 0; i--) {
			if (this.dynCount[i] != 0)
				return i;
		}
		return 0;
	}

	public void setDynamism(int x, int y, int z, int distance) {
		int prevDistance = getDynamism(x, y, z);
		if (prevDistance != distance) {
			this.dynamism.set(x, y, z, distance);
			this.dynCount[prevDistance]--;
			this.dynCount[distance]++;
			this.dirty = true;
		}
	}

	public int getDynamism(int x, int y, int z) {
		return this.dynamism.get(x, y, z);
	}

	public void clearDynamism() {
		this.dynamism = new DataLayer();
		Arrays.fill(this.dynCount, 0);
		this.dynCount[0] = 4096;
		this.dirty = true;
	}

	public void collectDynamicPositions(DynamicSection section) {
		if (this.dynCount[0] == 4096)
			return;

		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					int d = getDynamism(x, y, z);
					if (d != 0)
						section.add(x, y, z, d);
				}
			}
		}
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeShort(this.nonAirBlocks);
		this.states.write(buf);
	}

	public int getSerializedSize() {
		return 2 + this.states.getSerializedSize();
	}

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putByte("Y", (byte) (this.minY >> 4));

		this.states.write(tag, "Palette", "BlockStates");
		tag.putByteArray("Dynamism", Arrays.copyOf(this.dynamism.getData(), 2048));

		return tag;
	}

	public void deserialize(CompoundTag tag) {
		readStates(tag.getList("Palette", 10), tag.getLongArray("BlockStates"));

		this.dynamism = new DataLayer(tag.getByteArray("Dynamism"));
		recalculateDynCount();
	}

	private void recalculateDynCount() {
		Arrays.fill(this.dynCount, 0);
		for (byte b : this.dynamism.getData()) {
			this.dynCount[b & 15]++;
			this.dynCount[(b >> 4) & 15]++;
		}
	}
}
