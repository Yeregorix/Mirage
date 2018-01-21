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

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.*;
import net.smoofyuniverse.antixray.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.antixray.impl.network.cache.BlockContainerSnapshot;
import org.spongepowered.api.block.BlockState;

import javax.annotation.Nullable;
import java.util.Set;

public class NetworkBlockContainer implements IBlockStatePaletteResizer {
	@SuppressWarnings("deprecation")
	private static final ObjectIntIdentityMap<IBlockState> BLOCK_STATE_IDS = Block.BLOCK_STATE_IDS;
	private static final IBlockStatePalette REGISTRY_BASED_PALETTE = BlockStateContainer.REGISTRY_BASED_PALETTE;
	private static final IBlockState AIR_BLOCK_STATE = BlockStateContainer.AIR_BLOCK_STATE;

	private BlockStateContainer container;
	private IBlockStatePalette palette;
	private BitArray storage;
	private int bits;
	private int y = -1;

	public NetworkBlockContainer(BlockStateContainer container) {
		this.container = container;
	}

	public InternalBlockContainer getInternalBlockContainer() {
		return (InternalBlockContainer) this.container;
	}

	public void deobfuscate(ChunkChangeListener listener) {
		for (int i = 0; i < 4096; i++) {
			IBlockState fakeBlock = get(i), realBlock = this.container.get(i);
			if (fakeBlock != realBlock) {
				set(i, realBlock);
				listener.addChange(i & 15, this.y + (i >> 8 & 15), i >> 4 & 15, (BlockState) realBlock);
			}
		}
	}

	public IBlockState get(int index) {
		IBlockState block = this.palette.getBlockState(this.storage.getAt(index));
		return block == null ? AIR_BLOCK_STATE : block;
	}

	public boolean deobfuscate(ChunkChangeListener listener, int x, int y, int z) {
		int i = getIndex(x, y, z);
		IBlockState fakeBlock = get(i), realBlock = this.container.get(i);
		if (fakeBlock != realBlock) {
			set(i, realBlock);
			listener.addChange(x, this.y + y, z, (BlockState) realBlock);
			return true;
		}
		return false;
	}

	public int getY() {
		return this.y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void obfuscate(ChunkChangeListener listener, Set<?> ores, IBlockState ground) {
		for (int i = 0; i < 4096; i++) {
			IBlockState block = this.container.get(i);
			if (ores.contains(block)) {
				set(i, ground);
				listener.addChange(i & 15, this.y + (i >> 8 & 15), i >> 4 & 15, (BlockState) ground);
			}
		}
	}

	public void setBits(int bitsIn) {
		if (bitsIn != this.bits) {
			this.bits = bitsIn;

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

	public void set(int x, int y, int z, IBlockState state) {
		set(getIndex(x, y, z), state);
	}

	public static int getIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	public void set(int index, IBlockState state) {
		int i = this.palette.idFor(state);
		this.storage.setAt(index, i);
	}

	public IBlockState get(int x, int y, int z) {
		return get(getIndex(x, y, z));
	}

	@Override
	public int onResize(int bits, IBlockState state) {
		BitArray oldStorage = this.storage;
		IBlockStatePalette oldPalette = this.palette;
		setBits(bits);

		for (int i = 0; i < 4096; i++) {
			IBlockState block = oldPalette.getBlockState(oldStorage.getAt(i));
			if (block != null)
				set(i, block);
		}

		return this.palette.idFor(state);
	}

	public void write(PacketBuffer buf) {
		buf.writeByte(this.bits);
		this.palette.write(buf);
		buf.writeLongArray(this.storage.getBackingLongArray());
	}

	public void save(BlockContainerSnapshot out) {
		byte[] blockIds = new byte[4096];
		NibbleArray data = new NibbleArray();
		NibbleArray extension = getDataForNBT(blockIds, data);

		out.setSection(this.y >> 4);
		out.setBlockIds(blockIds);
		out.setData(data.getData());
		out.setExtension(extension == null ? null : extension.getData());
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

	public void load(BlockContainerSnapshot in) {
		if (in.getSection() != this.y >> 4)
			throw new IllegalArgumentException("Section");
		setDataFromNBT(in.getBlockIds(), new NibbleArray(in.getData()), in.getExtension() == null ? null : new NibbleArray(in.getExtension()));
	}

	private void setDataFromNBT(byte[] blockIds, NibbleArray data, @Nullable NibbleArray extension) {
		for (int i = 0; i < 4096; i++) {
			int x = i & 15;
			int y = i >> 8 & 15;
			int z = i >> 4 & 15;
			int ext = extension == null ? 0 : extension.get(x, y, z);
			int id = ext << 12 | (blockIds[i] & 255) << 4 | data.get(x, y, z);
			set(i, BLOCK_STATE_IDS.getByValue(id));
		}
	}

	public int getSerializedSize() {
		return 1 + this.palette.getSerializedSize() + PacketBuffer.getVarIntSize(this.storage.size()) + this.storage.getBackingLongArray().length * 8;
	}
}
