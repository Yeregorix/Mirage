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

package com.thomas15v.noxray.modifications.internal;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.*;

public class NetworkBlockContainer implements BlockStatePaletteResizer {
	private static final IBlockStatePalette REGISTRY_BASED_PALETTE = BlockStateContainer.REGISTRY_BASED_PALETTE;
	private static final IBlockState AIR_BLOCK_STATE = BlockStateContainer.AIR_BLOCK_STATE;
	private IBlockStatePalette palette;
	private BitArray storage;
	private int bits;
	private int y = -1;

	public int getY() {
		return this.y;
	}

	public void setY(int y) {
		this.y = y;
	}

	@Override
	public int onResize(int bits, IBlockState state) {
		BitArray bitarray = this.storage;
		IBlockStatePalette iblockstatepalette = this.palette;
		setBits(bits);

		for (int i = 0; i < bitarray.size(); ++i) {
			IBlockState iblockstate = iblockstatepalette.getBlockState(bitarray.getAt(i));
			if (iblockstate != null)
				set(i, iblockstate);
		}

		return this.palette.idFor(state);
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
				this.bits = MathHelper.log2DeBruijn(Block.BLOCK_STATE_IDS.size());
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

	public IBlockState get(int index) {
		IBlockState iblockstate = this.palette.getBlockState(this.storage.getAt(index));
		return iblockstate == null ? AIR_BLOCK_STATE : iblockstate;
	}

	public void write(PacketBuffer buf) {
		buf.writeByte(this.bits);
		this.palette.write(buf);
		buf.writeLongArray(this.storage.getBackingLongArray());
	}

	public int getSerializedSize() {
		return 1 + this.palette.getSerializedSize() + PacketBuffer.getVarIntSize(this.storage.size()) + this.storage.getBackingLongArray().length * 8;
	}
}
