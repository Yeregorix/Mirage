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

package net.smoofyuniverse.noxray.impl.network;

import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.noxray.NoXrayTimings;
import net.smoofyuniverse.noxray.api.ViewModifier;
import net.smoofyuniverse.noxray.api.volume.ChunkView;
import net.smoofyuniverse.noxray.config.Options;
import net.smoofyuniverse.noxray.impl.internal.InternalChunk;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Represent a chunk viewed for the network (akka online players)
 */
public class NetworkChunk implements ChunkView {
	public static final Vector3i BLOCK_MIN = Vector3i.ZERO, BLOCK_MAX = new Vector3i(15, 255, 15), BLOCK_SIZE = new Vector3i(16, 256, 16);

	private State state = State.NOT_OBFUSCATED;
	private NetworkBlockContainer[] containers;
	private ChunkChangeListener listener;
	private InternalChunk chunk;
	private NetworkWorld world;
	private final int x, z;

	public NetworkChunk(NetworkBlockContainer[] containers, InternalChunk chunk, NetworkWorld world) {
		this.containers = containers;
		this.chunk = chunk;
		this.world = world;
		this.listener = new ChunkChangeListener((Chunk) this.chunk);
		Vector3i pos = getPosition();
		this.x = pos.getX();
		this.z = pos.getZ();
	}

	public State getState() {
		return this.state;
	}

	public void onSending() {
		obfuscate();
		this.listener.clearChanges();
	}

	@Override
	public Vector3i getBlockMin() {
		return BLOCK_MIN;
	}

	@Override
	public Vector3i getBlockMax() {
		return BLOCK_MAX;
	}

	@Override
	public Vector3i getBlockSize() {
		return BLOCK_SIZE;
	}

	@Override
	public boolean containsBlock(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < 16 && y < 256 && z < 16;
	}

	@Override
	public BlockState getBlock(int x, int y, int z) {
		checkBounds(x, y, z);
		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null ? BlockTypes.AIR.getDefaultState() : (BlockState) c.get(x, y & 15, z);
	}

	@Override
	public BlockType getBlockType(int x, int y, int z) {
		return getBlock(x, y, z).getType();
	}

	@Override
	public UnmodifiableBlockVolume getUnmodifiableBlockView() {
		throw new UnsupportedOperationException();
	}

	@Override
	public MutableBlockVolume getBlockCopy(StorageType type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ImmutableBlockVolume getImmutableBlockCopy() {
		throw new UnsupportedOperationException();
	}

	private void checkBounds(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			throw new PositionOutOfBoundsException(new Vector3i(x, y, z), BLOCK_MIN, BLOCK_MAX);
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		checkBounds(x, y, z);

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container == null)
			return false;

		container.set(x, y & 15, z, (IBlockState) block);
		this.listener.addChange(x, y, z, (IBlockState) block);
		return true;
	}

	@Override
	public MutableBlockVolume getBlockView(Vector3i newMin, Vector3i newMax) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MutableBlockVolume getBlockView(DiscreteTransform3 transform) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MutableBlockVolumeWorker<? extends MutableBlockVolume> getBlockWorker() {
		throw new UnsupportedOperationException();
	}

	public void sendBlockChanges() {
		this.listener.sendChanges();
	}

	@Override
	public InternalChunk getStorage() {
		return this.chunk;
	}

	@Override
	public NetworkWorld getWorld() {
		return this.world;
	}

	@Override
	public Vector3i getPosition() {
		return this.chunk.getPosition();
	}

	@Override
	public boolean isExpositionCheckReady() {
		return isNeighborLoaded(Direction.NORTH) && isNeighborLoaded(Direction.SOUTH) && isNeighborLoaded(Direction.EAST) && isNeighborLoaded(Direction.WEST);
	}

	@Override
	public boolean isObfuscated() {
		return this.state != State.NOT_OBFUSCATED;
	}

	@Override
	public void obfuscate() {
		if (this.state == State.OBFUSCATED)
			return;

		// TODO cache

		NoXrayTimings.OBFUSCATION.startTiming();

		ViewModifier mod = this.world.getModifier();
		Timing timing = mod.getTiming();

		timing.startTiming();
		boolean ready = mod.isReady(this);
		timing.stopTiming();

		if (this.state == State.NEED_REOBFUSCATION) {
			if (!ready)
				return;

			deobfuscate();
		}

		if (ready) {
			timing.startTiming();
			mod.modify(this, ThreadLocalRandom.current());
			timing.stopTiming();

			this.state = State.OBFUSCATED;
		} else {
			NoXrayTimings.FAST_PRE_MODIFIER.startTiming();

			Options options = this.world.getOptions();
			for (NetworkBlockContainer c : this.containers) {
				if (c != null)
					c.obfuscate(this.listener, options.oresSet, (IBlockState) options.ground);
			}

			NoXrayTimings.FAST_PRE_MODIFIER.stopTiming();
			this.state = State.NEED_REOBFUSCATION;
		}

		NoXrayTimings.OBFUSCATION.stopTiming();
	}

	@Override
	public void deobfuscate() {
		if (this.state == State.NOT_OBFUSCATED)
			return;

		NoXrayTimings.DEOBFUSCATION.startTiming();

		for (NetworkBlockContainer c : this.containers) {
			if (c != null)
				c.deobfuscate(this.listener);
		}

		this.state = State.NOT_OBFUSCATED;

		NoXrayTimings.DEOBFUSCATION.stopTiming();
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		checkBounds(x, y, z);
		if (this.state == State.NOT_OBFUSCATED)
			return false;

		NetworkBlockContainer container = this.containers[y >> 4];
		return container != null && container.deobfuscate(this.listener, x, y & 15, z);
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		checkBounds(x, y, z);

		if (y != 255 && notFullCube1(x, y + 1, z))
			return true;
		if (y != 0 && notFullCube1(x, y - 1, z))
			return true;

		return notFullCube2(x + 1, y, z) || notFullCube2(x - 1, y, z) || notFullCube2(x, y, z + 1) || notFullCube2(x, y, z - 1);
	}

	private boolean notFullCube1(int x, int y, int z) {
		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null || !c.get(x, y & 15, z).isFullCube();
	}

	private boolean notFullCube2(int x, int y, int z) {
		int dx = 0, dz = 0;
		if (x < 0) {
			dx--;
			x += 16;
		} else if (x >= 16) {
			dx++;
			x -= 16;
		}
		if (z < 0) {
			dz--;
			z += 16;
		} else if (z >= 16) {
			dz++;
			z -= 16;
		}

		if (dx == 0 && dz == 0)
			return notFullCube1(x, y, z);

		NetworkChunk neighbor = this.world.getChunk(this.x + dx, this.z + dz);
		return neighbor == null || neighbor.notFullCube1(x, y, z);
	}

	public enum State {
		NOT_OBFUSCATED, NEED_REOBFUSCATION, OBFUSCATED
	}
}
