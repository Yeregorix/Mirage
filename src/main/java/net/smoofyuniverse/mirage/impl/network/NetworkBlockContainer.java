/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.*;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.mirage.impl.network.cache.BlockContainerSnapshot;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamism.DynamicChunk;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;

import static net.smoofyuniverse.mirage.util.MathUtil.lengthSquared;
import static net.smoofyuniverse.mirage.util.MathUtil.squared;

public class NetworkBlockContainer implements IBlockStatePaletteResizer {

	@SuppressWarnings("deprecation")
	private static final ObjectIntIdentityMap<IBlockState> BLOCK_STATE_IDS = Block.BLOCK_STATE_IDS;
	private static final IBlockStatePalette REGISTRY_BASED_PALETTE = BlockStateContainer.REGISTRY_BASED_PALETTE;
	private static final IBlockState AIR_BLOCK_STATE = BlockStateContainer.AIR_BLOCK_STATE;

	private BlockStateContainer container;

	private IBlockStatePalette palette;
	private NibbleArray dynamism;
	private BitArray storage;
	private int bits, minY = -1;

	private int[] dynCount = new int[16];
	private int blockCount;

	boolean dirty = false;

	public NetworkBlockContainer(BlockStateContainer container) {
		this.container = container;
		this.dynamism = new NibbleArray();
		this.dynCount[0] = 4096;
	}

	public InternalBlockContainer getInternalBlockContainer() {
		return (InternalBlockContainer) this.container;
	}

	public int getY() {
		return this.minY;
	}

	public void setY(int y) {
		this.minY = y;
	}

	public boolean isEmpty() {
		return this.blockCount == 0;
	}

	public IBlockState get(int index) {
		IBlockState state = this.palette.getBlockState(this.storage.getAt(index));
		return state == null ? AIR_BLOCK_STATE : state;
	}

	private void _set(int index, IBlockState state) {
		int i = this.palette.idFor(state);
		this.storage.setAt(index, i);
		this.dirty = true;
	}

	public void setBits(int bits) {
		if (this.bits != bits) {
			this.bits = bits;

			if (this.bits <= 4) {
				this.bits = 4;
				this.palette = new BlockStatePaletteLinear(this.bits, this);
			} else if (this.bits <= 8) {
				this.palette = new BlockStatePaletteHashMap(this.bits, this);
			} else {
				this.palette = REGISTRY_BASED_PALETTE;
				this.bits = MathHelper.log2DeBruijn(BLOCK_STATE_IDS.size());
			}

			this.palette.idFor(AIR_BLOCK_STATE);
			this.storage = new BitArray(this.bits, 4096);
		}
	}

	public void deobfuscate(ChunkChangeListener listener) {
		this.blockCount = 0;
		for (int i = 0; i < 4096; i++) {
			IBlockState fakeState = get(i), realState = this.container.get(i);

			if (realState.getBlock() != Blocks.AIR)
				this.blockCount++;

			if (fakeState != realState) {
				_set(i, realState);

				if (listener != null)
					listener.addChange(i & 15, this.minY + (i >> 8 & 15), i >> 4 & 15);
			}
		}
	}

	public boolean deobfuscate(ChunkChangeListener listener, int x, int y, int z) {
		int i = index(x, y, z);
		IBlockState fakeState = get(i), realState = this.container.get(i);

		if (fakeState != realState) {
			if (realState.getBlock() == Blocks.AIR)
				this.blockCount--;
			else if (fakeState.getBlock() == Blocks.AIR)
				this.blockCount++;

			setDynamism(i, 0);
			_set(i, realState);

			if (listener != null) {
				listener.updateDynamism(x, this.minY + y, z, 0);
				listener.addChange(x, this.minY + y, z);
			}
			return true;
		}
		return false;
	}

	public static int index(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	public void setDynamism(int index, int distance) {
		int oldD = this.dynamism.getFromIndex(index);
		if (oldD != distance) {
			this.dynamism.setIndex(index, distance);
			this.dynCount[oldD]--;
			this.dynCount[distance]++;
			this.dirty = true;
		}
	}

	public void preobfuscate(ChunkChangeListener listener, Set<?> ores, IBlockState ground) {
		boolean notAirGround = ground.getBlock() != Blocks.AIR;

		this.blockCount = 0;
		for (int i = 0; i < 4096; i++) {
			IBlockState state = get(i);

			if (ores.contains(state)) {
				if (notAirGround)
					this.blockCount++;

				_set(i, ground);

				if (listener != null)
					listener.addChange(i & 15, this.minY + (i >> 8 & 15), i >> 4 & 15);
			} else {
				if (state.getBlock() != Blocks.AIR)
					this.blockCount++;
			}
		}
	}

	public int getMaxDynamism() {
		for (int i = 10; i >= 0; i--) {
			if (this.dynCount[i] != 0)
				return i;
		}
		return 0;
	}

	public void clearDynamism() {
		this.dynamism = new NibbleArray();
		Arrays.fill(this.dynCount, 0);
		this.dynCount[0] = 4096;
		this.dirty = true;
	}

	public void setDynamism(int x, int y, int z, int distance) {
		setDynamism(index(x, y, z), distance);
	}

	public int getDynamism(int x, int y, int z) {
		return getDynamism(index(x, y, z));
	}

	public int getDynamism(int index) {
		return this.dynamism.getFromIndex(index);
	}

	public void collectDynamicPositions(DynamicChunk chunk, int cX, int cY, int cZ) {
		if (this.dynCount[0] == 4096)
			return;

		for (int i = 0; i < 4096; i++) {
			int d = this.dynamism.getFromIndex(i);
			if (d != 0) {
				int x = i & 15, y = i >> 8 & 15, z = i >> 4 & 15;
				if (lengthSquared(cX - x, cY - y, cZ - z) <= squared(d) << 8)
					chunk.add(x, this.minY + y, z);
			}
		}
	}

	public IBlockState get(int x, int y, int z) {
		return get(index(x, y, z));
	}

	public void set(int index, IBlockState state) {
		IBlockState oldState = get(index);
		if (oldState != state) {
			if (oldState.getBlock() == Blocks.AIR)
				this.blockCount++;
			else if (state.getBlock() == Blocks.AIR)
				this.blockCount--;

			_set(index, state);
		}
	}

	@Override
	public int onResize(int bits, IBlockState state) {
		BitArray oldStorage = this.storage;
		IBlockStatePalette oldPalette = this.palette;
		setBits(bits);

		for (int i = 0; i < 4096; i++) {
			IBlockState block = oldPalette.getBlockState(oldStorage.getAt(i));
			if (block != null)
				_set(i, block);
		}

		return this.palette.idFor(state);
	}

	public void write(PacketBuffer buf) {
		buf.writeByte(this.bits);
		this.palette.write(buf);
		buf.writeLongArray(this.storage.getBackingLongArray());
	}

	public void set(int x, int y, int z, IBlockState state) {
		set(index(x, y, z), state);
	}

	@Nullable
	private NibbleArray getDataForNBT(byte[] blockIds, NibbleArray data) {
		NibbleArray extension = null;

		for (int i = 0; i < 4096; i++) {
			int id = BLOCK_STATE_IDS.get(get(i));
			int x = i & 15;
			int y = i >> 8 & 15;
			int z = i >> 4 & 15;

			if ((id >> 12 & 15) != 0) {
				if (extension == null)
					extension = new NibbleArray();

				extension.set(x, y, z, id >> 12 & 15);
			}

			blockIds[i] = (byte) (id >> 4 & 255);
			data.set(x, y, z, id & 15);
		}

		return extension;
	}

	public void save(BlockContainerSnapshot out) {
		byte[] blockIds = new byte[4096];
		NibbleArray data = new NibbleArray();
		NibbleArray extension = getDataForNBT(blockIds, data);
		byte[] dynamism = this.dynamism.getData().clone();

		out.setSection(this.minY >> 4);
		out.setBlockIds(blockIds);
		out.setData(data.getData());
		out.setExtension(extension == null ? null : extension.getData());
		out.setDynamism(dynamism);
	}

	public void load(BlockContainerSnapshot in) {
		if (in.getSection() != this.minY >> 4)
			throw new IllegalArgumentException("Section");

		setDataFromNBT(in.getBlockIds(), new NibbleArray(in.getData()), in.getExtension() == null ? null : new NibbleArray(in.getExtension()));
		this.dynamism = new NibbleArray(in.getDynamism().clone());
		recalculateDynCount();
		this.dirty = true;
	}

	private void setDataFromNBT(byte[] blockIds, NibbleArray data, @Nullable NibbleArray extension) {
		this.blockCount = 0;
		for (int i = 0; i < 4096; i++) {
			int x = i & 15;
			int y = i >> 8 & 15;
			int z = i >> 4 & 15;
			int ext = extension == null ? 0 : extension.get(x, y, z);
			int id = ext << 12 | (blockIds[i] & 255) << 4 | data.get(x, y, z);

			IBlockState block = BLOCK_STATE_IDS.getByValue(id);
			if (block.getBlock() != Blocks.AIR)
				this.blockCount++;

			_set(i, block);
		}
	}

	public int getSerializedSize() {
		return 1 + this.palette.getSerializedSize() + PacketBuffer.getVarIntSize(this.storage.size()) + this.storage.getBackingLongArray().length * 8;
	}

	private void recalculateDynCount() {
		Arrays.fill(this.dynCount, 0);
		for (int i = 0; i < 4096; i++)
			this.dynCount[this.dynamism.getFromIndex(i)]++;
	}
}
