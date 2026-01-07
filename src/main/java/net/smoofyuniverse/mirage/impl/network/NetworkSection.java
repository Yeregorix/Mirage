/*
 * Copyright (c) 2018-2026 Hugo Dupanloup (Yeregorix)
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

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;

import java.util.Arrays;

public class NetworkSection {
	// Redo PalettedContainerFactory#create but without RegistryAccess
	private static final Strategy<BlockState> blockStatesStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
	private static final BlockState defaultBlockState = Blocks.AIR.defaultBlockState();
	private static final Codec<PalettedContainer<BlockState>> blockStatesContainerCodec = PalettedContainer.codecRW(BlockState.CODEC, blockStatesStrategy, defaultBlockState);

	private final LevelChunkSection section;
	int minY = 0;

	private PalettedContainer<BlockState> states;
	private DataLayer dynamism;

	private final int[] dynCount = new int[16];
	private int nonAirBlocks;

	boolean dirty = false;

	public NetworkSection(LevelChunkSection section) {
		this.section = section;

		this.states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStatesStrategy);
		this.dynamism = new DataLayer();
		this.dynCount[0] = 4096;
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

	public boolean hasOnlyAir() {
		return this.nonAirBlocks == 0;
	}

	public boolean hasNoDynamism() {
		return this.dynCount[0] == 4096;
	}

	public void deobfuscate(ChunkChangeListener listener) {
		if (hasOnlyAir() && this.section.hasOnlyAir()) {
			return;
		}

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
		if (this.hasNoDynamism())
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

		tag.put("BlockStates", blockStatesContainerCodec.encodeStart(NbtOps.INSTANCE, this.states).getOrThrow());
		tag.putByteArray("Dynamism", Arrays.copyOf(this.dynamism.getData(), 2048));

		return tag;
	}

	public void deserialize(CompoundTag tag) {
		this.states = blockStatesContainerCodec.parse(NbtOps.INSTANCE, tag.getCompound("BlockStates").get()).getOrThrow();
		recalculateAirBlocks();

        this.dynamism = new DataLayer(tag.getByteArray("Dynamism").get());
		recalculateDynCount();

		this.dirty = true;
	}

	private void recalculateDynCount() {
		Arrays.fill(this.dynCount, 0);
		for (byte b : this.dynamism.getData()) {
			this.dynCount[b & 15]++;
			this.dynCount[(b >> 4) & 15]++;
		}
	}
}
