/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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
import com.thomas15v.noxray.NoXrayPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.BlockStatePaletteHashMap;
import net.minecraft.world.chunk.BlockStatePaletteLinear;
import net.minecraft.world.chunk.BlockStatePaletteResizer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class NetworkBlockContainer implements BlockStatePaletteResizer {

	private BitArray storage;
	private IBlockStatePalette palette;
	private int bits;

	private IBlockStatePalette REGISTRY_BASED_PALETTE;
	private IBlockState AIR_BLOCK_STATE;

	private int y = -1;

	public NetworkBlockContainer(IBlockStatePalette REGISTRY_BASED_PALETTE, IBlockState AIR_BLOCK_STATE) {
		this.REGISTRY_BASED_PALETTE = REGISTRY_BASED_PALETTE;
		this.AIR_BLOCK_STATE = AIR_BLOCK_STATE;
	}

	@Override
	public int onResize(int size, IBlockState state) {
		BitArray bitarray = this.storage;
		IBlockStatePalette iblockstatepalette = this.palette;
		setBits(size);
		for (int i = 0; i < bitarray.size(); ++i) {
			IBlockState iblockstate = iblockstatepalette.getBlockState(bitarray.getAt(i));

			if (iblockstate != null) {
				set(i, iblockstate);
			}
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
				this.palette = this.REGISTRY_BASED_PALETTE;
				this.bits = MathHelper.log2DeBruijn(Block.BLOCK_STATE_IDS.size());
			}
			this.palette.idFor(this.AIR_BLOCK_STATE);
			this.storage = new BitArray(this.bits, 4096);
		}
	}

	public void set(int index, IBlockState state) {
		int i = this.palette.idFor(state);
		this.storage.setAt(index, i);
	}

	public void set(int x, int y, int z, IBlockState blockState) {
		set(getIndex(x, y, z), blockState);
	}

	private static int getIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	public int size() {
		return 1 + this.palette.getSerializedSize() + PacketBuffer.getVarIntSize(this.storage.size()) + this.storage.getBackingLongArray().length * 8;
	}

	public void write(PacketBuffer buf) {
		buf.writeByte(this.bits);
		this.palette.write(buf);
		buf.writeLongArray(this.storage.getBackingLongArray());
	}

	public void setY(int y) {
		this.y = y;
	}

	public BlockState get(Vector3i vector3i) {
		return (BlockState) this.get(vector3i.getX(), vector3i.getY(), vector3i.getZ());
	}

	public IBlockState get(int x, int y, int z) {
		return get(getIndex(x, y, z));
	}

	protected IBlockState get(int index) {
		IBlockState iblockstate = this.palette.getBlockState(this.storage.getAt(index));
		return iblockstate == null ? this.AIR_BLOCK_STATE : iblockstate;
	}

	public void obfuscate(NetworkChunk chunk) {
		BlockModifier blockModifier = NoXrayPlugin.getInstance().getBlockModifier();
		Predicate<BlockState> filter = blockModifier.getFilter();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					obfuscateBlock(filter,
							chunk.getWorld().getLocation(x + chunk.getLocation().getX() * 16, y + this.y, z + chunk.getLocation().getZ() * 16),
							chunk,
							blockModifier);
				}
			}
		}
	}

	private void obfuscateBlock(Predicate<BlockState> filter, Location<World> location, NetworkChunk chunk, BlockModifier blockModifier) {
		BlockState blockState = location.getBlock();
		if (filter.test(blockState)) {
			BlockState response = blockModifier.handleBlock(blockState, location, this.getSurrounding(location));
			if (response != blockState) {
				chunk.set(location, response);
			}
		}
	}

	private final static Direction[] directions = new Direction[]{Direction.EAST,
			Direction.NORTH, Direction.SOUTH, Direction.WEST};

	public List<BlockState> getSurrounding(Location<World> location) {
		List<BlockState> blockStates = new ArrayList<BlockState>() {
			@Override
			public boolean add(BlockState blockState) {
				return blockState != null && super.add(blockState);
			}
		};

		if (location.getY() != 256) {
			blockStates.add(location.getRelative(Direction.UP).getBlock());
		}
		if (location.getY() != 0) {
			blockStates.add(location.getRelative(Direction.DOWN).getBlock());
		}
		for (Direction direction : directions) {
			blockStates.add(location.getRelative(direction).getBlock());
		}
		if (blockStates.size() < 5) {
			System.out.println("WARNING FAULTY CODE: " + blockStates + " " + location);
		}
		return blockStates;
	}

}
