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

import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.smoofyuniverse.antixray.AntiXrayTimings;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifier;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import net.smoofyuniverse.antixray.config.Options;
import net.smoofyuniverse.antixray.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.network.cache.BlockContainerSnapshot;
import net.smoofyuniverse.antixray.impl.network.cache.ChunkSnapshot;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a chunk viewed for the network (akka online players)
 */
public class NetworkChunk implements ChunkView {
	public static final Vector3i BLOCK_MIN = Vector3i.ZERO, BLOCK_MAX = new Vector3i(15, 255, 15), BLOCK_SIZE = new Vector3i(16, 256, 16);

	private State state = State.NOT_OBFUSCATED;
	private NetworkBlockContainer[] containers;
	private ChunkChangeListener listener;
	private Vector3i mutableMax = BLOCK_MIN;
	private InternalChunk chunk;
	private NetworkWorld world;
	private final int x, z;
	private Random random = new Random();
	private boolean saved = true;
	private long seed;

	public NetworkChunk(InternalChunk chunk, NetworkWorld world) {
		this.chunk = chunk;
		this.world = world;
		this.listener = new ChunkChangeListener((Chunk) this.chunk);
		Vector3i pos = getPosition();
		this.x = pos.getX();
		this.z = pos.getZ();

		long wSeed = world.getConfig().seed;
		this.random.setSeed(wSeed);
		long k = this.random.nextLong() / 2L * 2L + 1L;
		long l = this.random.nextLong() / 2L * 2L + 1L;
		this.seed = (long) this.x * k + (long) this.z * l ^ wSeed;
	}

	public void setContainers(ExtendedBlockStorage[] storages) {
		NetworkBlockContainer[] containers = new NetworkBlockContainer[storages.length];
		for (int i = 0; i < storages.length; i++) {
			if (storages[i] != null)
				containers[i] = ((InternalBlockContainer) storages[i].getData()).getNetworkBlockContainer();
		}
		setContainers(containers);
	}

	public void setContainers(NetworkBlockContainer[] containers) {
		if (this.state != State.NOT_OBFUSCATED)
			throw new IllegalStateException();

		if (this.containers != null) {
			for (NetworkBlockContainer c : this.containers) {
				if (c != null)
					c.getInternalBlockContainer().setNetworkChunk(null);
			}
		}

		int maxY = 0;
		if (containers != null) {
			for (NetworkBlockContainer c : containers) {
				if (c != null) {
					c.getInternalBlockContainer().setNetworkChunk(this);
					int y = c.getY() + 15;
					if (maxY < y)
						maxY = y;
				}
			}
		}
		this.containers = containers;
		this.mutableMax = new Vector3i(15, maxY, 15);
	}

	public State getState() {
		return this.state;
	}

	public boolean isSaved() {
		return this.saved;
	}

	public void setSaved(boolean value) {
		this.saved = value;
	}

	public void saveToCacheLater() {
		if (shouldSave()) {
			long date;
			if (this.world.useCache()) {
				ChunkSnapshot chunk = save(new ChunkSnapshot());
				this.world.addPendingSave(this.x, this.z, chunk);
				date = chunk.getDate();
			} else
				date = System.currentTimeMillis();
			this.chunk.setValidCacheDate(date);
			((Chunk) this.chunk).markDirty();
			this.saved = true;
		}
	}

	public boolean shouldSave() {
		return !this.saved && this.state == State.OBFUSCATED;
	}

	public ChunkSnapshot save(ChunkSnapshot out) {
		List<BlockContainerSnapshot> list = new ArrayList<>(this.containers.length);
		for (NetworkBlockContainer container : this.containers) {
			if (container != null) {
				BlockContainerSnapshot data = new BlockContainerSnapshot();
				container.save(data);
				list.add(data);
			}
		}
		out.setContainers(list.toArray(new BlockContainerSnapshot[list.size()]));
		out.setDate(System.currentTimeMillis());
		return out;
	}

	public void saveToCacheNow() {
		if (shouldSave()) {
			long date;
			if (this.world.useCache()) {
				ChunkSnapshot chunk = save(new ChunkSnapshot());
				this.world.removePendingSave(this.x, this.z);
				this.world.saveToCache(this.x, this.z, chunk);
				date = chunk.getDate();
			} else
				date = System.currentTimeMillis();
			this.chunk.setValidCacheDate(date);
			((Chunk) this.chunk).markDirty();
			this.saved = true;
		}
	}

	public void loadFromCacheNow() {
		if (this.world.useCache()) {
			ChunkSnapshot chunk = this.world.readFromCache(this.x, this.z);
			if (chunk != null && chunk.getDate() == this.chunk.getValidCacheDate()) {
				this.world.removePendingSave(this.x, this.z);
				load(chunk);

				this.state = State.OBFUSCATED;
				this.saved = true;
			}
		}
	}

	public void load(ChunkSnapshot in) {
		for (BlockContainerSnapshot data : in.getContainers()) {
			NetworkBlockContainer container = this.containers[data.getSection()];
			if (container != null)
				container.load(data);
		}
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
			return false; // AntiXray is designed to replace already existing blocks, not to create new ones

		container.set(x, y & 15, z, (IBlockState) block);
		this.listener.addChange(x, y, z, block);
		this.saved = false;
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

	public ChunkChangeListener getListener() {
		return this.listener;
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

		AntiXrayTimings.OBFUSCATION.startTiming();

		ChunkModifier mod = this.world.getModifier();
		Timing timing = mod.getTiming();

		timing.startTiming();
		boolean ready = mod.isReady(this);
		timing.stopTiming();

		if (this.state == State.NEED_REOBFUSCATION) {
			if (!ready) {
				AntiXrayTimings.OBFUSCATION.stopTiming();
				return;
			}

			if (this.world.getConfig().usePreobf)
				deobfuscate();
		}

		if (ready) {
			this.random.setSeed(this.seed);

			timing.startTiming();
			mod.modify(this, this.random);
			timing.stopTiming();

			this.state = State.OBFUSCATED;
		} else {
			if (this.world.getConfig().usePreobf) {
				AntiXrayTimings.PREOBFUSCATION.startTiming();

				Options options = this.world.getOptions();
				for (NetworkBlockContainer c : this.containers) {
					if (c != null)
						c.obfuscate(this.listener, options.oresSet, (IBlockState) options.ground);
				}

				AntiXrayTimings.PREOBFUSCATION.stopTiming();
			}
			this.state = State.NEED_REOBFUSCATION;
		}

		AntiXrayTimings.OBFUSCATION.stopTiming();
	}

	@Override
	public void deobfuscate() {
		if (this.state == State.NOT_OBFUSCATED)
			return;

		AntiXrayTimings.DEOBFUSCATION.startTiming();

		for (NetworkBlockContainer c : this.containers) {
			if (c != null)
				c.deobfuscate(this.listener);
		}

		this.state = State.NOT_OBFUSCATED;

		AntiXrayTimings.DEOBFUSCATION.stopTiming();
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		checkBounds(x, y, z);
		if (this.state == State.NOT_OBFUSCATED)
			return false;

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container != null && container.deobfuscate(this.listener, x, y & 15, z)) {
			this.saved = false;
			return true;
		}
		return false;
	}

	@Override
	public Vector3i getMutableMax() {
		return this.mutableMax;
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

	public static long asLong(int x, int z) {
		return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
	}
}
