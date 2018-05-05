/*
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
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.AntiXrayTimings;
import net.smoofyuniverse.antixray.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import net.smoofyuniverse.antixray.config.PreobfuscationConfig;
import net.smoofyuniverse.antixray.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.network.cache.BlockContainerSnapshot;
import net.smoofyuniverse.antixray.impl.network.cache.ChunkSnapshot;
import net.smoofyuniverse.antixray.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.antixray.impl.network.dynamism.DynamicChunk;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.util.gen.ArrayImmutableBlockBuffer;
import org.spongepowered.common.util.gen.ArrayMutableBlockBuffer;
import org.spongepowered.common.world.extent.ExtentBufferUtil;
import org.spongepowered.common.world.extent.MutableBlockViewDownsize;
import org.spongepowered.common.world.extent.MutableBlockViewTransform;
import org.spongepowered.common.world.extent.UnmodifiableBlockVolumeWrapper;
import org.spongepowered.common.world.extent.worker.SpongeMutableBlockVolumeWorker;
import org.spongepowered.common.world.schematic.GlobalPalette;
import org.spongepowered.common.world.storage.SpongeChunkLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static net.smoofyuniverse.antixray.util.MathUtil.*;

/**
 * Represents a chunk viewed for the network (akka online players)
 */
public class NetworkChunk implements ChunkView {
	public static final int maxDynamismDistance = 160, maxDynamismDistance2 = squared(160);

	private final Vector3i position, blockMin, blockMax;
	private final InternalChunk chunk;
	private final NetworkWorld world;
	public final int x, z;
	private final boolean dynamismEnabled;
	private final long seed;

	private final Object containersLock = new Object();
	private NetworkBlockContainer[] containers;
	private State state = State.NOT_OBFUSCATED;
	private ChunkChangeListener listener;
	private Random random = new Random();
	private boolean saved = true;

	public NetworkChunk(InternalChunk chunk, NetworkWorld world) {
		this.chunk = chunk;
		this.world = world;
		this.position = chunk.getPosition();
		this.blockMin = chunk.getBlockMin();
		this.blockMax = chunk.getBlockMax();
		this.dynamismEnabled = world.isDynamismEnabled();
		this.x = this.position.getX();
		this.z = this.position.getZ();

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

		synchronized (this.containersLock) {
			if (this.containers != null) {
				for (NetworkBlockContainer c : this.containers) {
					if (c != null)
						c.getInternalBlockContainer().setNetworkChunk(null);
				}
			}

			if (containers != null) {
				for (NetworkBlockContainer c : containers) {
					if (c != null)
						c.getInternalBlockContainer().setNetworkChunk(this);
				}
			}
			this.containers = containers;
		}
	}

	public void setContainer(int index, ExtendedBlockStorage s) {
		setContainer(index, ((InternalBlockContainer) s.getData()).getNetworkBlockContainer());
	}

	public void setContainer(int index, NetworkBlockContainer c) {
		synchronized (this.containersLock) {
			if (this.containers == null || this.containers[index] != null)
				throw new UnsupportedOperationException();

			if (c != null) {
				c.getInternalBlockContainer().setNetworkChunk(this);
				this.containers[index] = c;
			}
		}
	}

	public boolean needContainer(int index) {
		synchronized (this.containersLock) {
			return this.containers != null && this.containers[index] == null;
		}
	}

	public Optional<ChunkChangeListener> getListener() {
		return Optional.ofNullable(this.listener);
	}

	public void setListener(ChunkChangeListener listener) {
		this.listener = listener;
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
		out.setContainers(list.toArray(new BlockContainerSnapshot[0]));
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
	public InternalChunk getStorage() {
		return this.chunk;
	}

	@Override
	public boolean isDynamismEnabled() {
		return this.dynamismEnabled;
	}

	@Override
	public void setDynamism(int x, int y, int z, int distance) {
		checkBounds(x, y, z);
		if (!this.dynamismEnabled)
			return;

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container == null) {
			this.chunk.bindOrCreateContainer(y >> 4);
			container = this.containers[y >> 4];
		}

		distance = clamp(distance, 0, 10);
		container.setDynamism(x & 15, y & 15, z & 15, distance);
		if (this.listener != null)
			this.listener.updateDynamism(x & 15, y, z & 15, distance);
		this.saved = false;
	}

	@Override
	public int getDynamism(int x, int y, int z) {
		checkBounds(x, y, z);
		if (!this.dynamismEnabled)
			return 0;

		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null ? 0 : c.getDynamism(x & 15, y & 15, z & 15);
	}

	public void collectDynamicPositions(DynamicChunk chunk) {
		if (!this.dynamismEnabled)
			return;

		Vector3i center = chunk.getCenter();
		if (center == null)
			return;

		int relX = center.getX() - (this.x << 4), relZ = center.getZ() - (this.z << 4);

		int minXZDistance2 = lengthSquared(clamp(relX, 0, 15) - relX, clamp(relZ, 0, 15) - relZ);
		if (minXZDistance2 + squared(clamp(center.getY(), 0, 255) - center.getY()) > maxDynamismDistance2)
			return;

		for (NetworkBlockContainer c : this.containers) {
			if (c != null) {
				int relY = center.getY() - c.getY();

				int d2 = minXZDistance2 + squared(clamp(relY, 0, 15) - relY);
				if (d2 > maxDynamismDistance2 || d2 > squared(c.getMaxDynamism()) << 8)
					continue;

				c.collectDynamicPositions(chunk, relX, relY, relZ);
			}
		}
	}

	@Override
	public void obfuscate() {
		if (this.state == State.OBFUSCATED)
			return;

		AntiXrayTimings.OBFUSCATION.startTiming();

		boolean ready = true;
		for (ConfiguredModifier mod : this.world.getModifiers()) {
			Timing timing = mod.modifier.getTiming();
			timing.startTiming();

			if (!mod.modifier.isReady(this, mod.config)) {
				ready = false;
				timing.stopTiming();
				break;
			}

			timing.stopTiming();
		}

		if (this.state == State.NEED_REOBFUSCATION) {
			if (!ready) {
				AntiXrayTimings.OBFUSCATION.stopTiming();
				return;
			}

			if (this.world.getConfig().preobf.enabled)
				deobfuscate();
		}

		if (ready) {
			this.random.setSeed(this.seed);

			for (ConfiguredModifier mod : this.world.getModifiers()) {
				Timing timing = mod.modifier.getTiming();
				timing.startTiming();

				try {
					mod.modifier.modify(this, this.random, mod.config);
				} catch (Exception ex) {
					AntiXray.LOGGER.error("Modifier " + mod.modifier.getId() + " has thrown an exception while modifying a network chunk", ex);
				}

				timing.stopTiming();
			}

			this.state = State.OBFUSCATED;
		} else {
			if (this.world.getConfig().preobf.enabled) {
				AntiXrayTimings.PREOBFUSCATION.startTiming();

				PreobfuscationConfig.Immutable cfg = this.world.getConfig().preobf;
				for (NetworkBlockContainer c : this.containers) {
					if (c != null) {
						c.clearDynamism();
						c.obfuscate(this.listener, cfg.blocks, (IBlockState) cfg.replacement);
					}
				}

				if (this.listener != null)
					this.listener.clearDynamism();

				AntiXrayTimings.PREOBFUSCATION.stopTiming();
			}
			this.state = State.NEED_REOBFUSCATION;
		}

		AntiXrayTimings.OBFUSCATION.stopTiming();
	}

	@Override
	public NetworkWorld getWorld() {
		return this.world;
	}

	@Override
	public Vector3i getPosition() {
		return this.position;
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		checkBounds(x, y, z);
		if (this.state == State.NOT_OBFUSCATED)
			return false;

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container != null && container.deobfuscate(this.listener, x & 15, y & 15, z & 15)) {
			this.saved = false;
			return true;
		}
		return false;
	}

	private void checkBounds(int x, int y, int z) {
		if (!containsBlock(x, y, z))
			throw new PositionOutOfBoundsException(new Vector3i(x, y, z), this.blockMin, this.blockMax);
	}

	@Override
	public Vector3i getBlockMin() {
		return this.blockMin;
	}

	@Override
	public Vector3i getBlockMax() {
		return this.blockMax;
	}

	@Override
	public Vector3i getBlockSize() {
		return SpongeChunkLayout.CHUNK_SIZE;
	}

	@Override
	public boolean containsBlock(int x, int y, int z) {
		return VecHelper.inBounds(x, y, z, this.blockMin, this.blockMax);
	}

	@Override
	public BlockType getBlockType(int x, int y, int z) {
		return getBlock(x, y, z).getType();
	}

	@Override
	public UnmodifiableBlockVolume getUnmodifiableBlockView() {
		return new UnmodifiableBlockVolumeWrapper(this);
	}

	@Override
	public MutableBlockVolume getBlockCopy(StorageType type) {
		switch (type) {
			case STANDARD:
				return new ArrayMutableBlockBuffer(GlobalPalette.instance, getBlockMin(), getBlockSize(), ExtentBufferUtil.copyToArray(this, getBlockMin(), getBlockMax(), getBlockSize()));
			case THREAD_SAFE:
			default:
				throw new UnsupportedOperationException(type.name());
		}
	}

	@Override
	public ImmutableBlockVolume getImmutableBlockCopy() {
		return ArrayImmutableBlockBuffer.newWithoutArrayClone(GlobalPalette.instance, getBlockMin(), getBlockSize(), ExtentBufferUtil.copyToArray(this, getBlockMin(), getBlockMax(), getBlockSize()));
	}

	@Override
	public BlockState getBlock(int x, int y, int z) {
		checkBounds(x, y, z);
		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null ? BlockTypes.AIR.getDefaultState() : (BlockState) c.get(x & 15, y & 15, z & 15);
	}

	@Override
	public MutableBlockVolume getBlockView(Vector3i newMin, Vector3i newMax) {
		return new MutableBlockViewDownsize(this, newMin, newMax);
	}

	@Override
	public MutableBlockVolume getBlockView(DiscreteTransform3 transform) {
		return new MutableBlockViewTransform(this, transform);
	}

	@Override
	public MutableBlockVolumeWorker<? extends MutableBlockVolume> getBlockWorker() {
		return new SpongeMutableBlockVolumeWorker<>(this);
	}

	@Override
	public boolean areNeighborsLoaded() {
		return this.world.isChunkLoaded(this.x + 1, this.z) && this.world.isChunkLoaded(this.x, this.z + 1) && this.world.isChunkLoaded(this.x - 1, this.z) && this.world.isChunkLoaded(this.x, this.z - 1);
	}

	@Override
	public boolean isObfuscated() {
		return this.state != State.NOT_OBFUSCATED;
	}

	@Override
	public void deobfuscate() {
		if (this.state == State.NOT_OBFUSCATED)
			return;

		AntiXrayTimings.DEOBFUSCATION.startTiming();

		for (NetworkBlockContainer c : this.containers) {
			if (c != null) {
				c.clearDynamism();
				c.deobfuscate(this.listener);
			}
		}

		if (this.listener != null)
			this.listener.clearDynamism();

		this.state = State.NOT_OBFUSCATED;

		AntiXrayTimings.DEOBFUSCATION.stopTiming();
	}

	@Override
	public void clearDynamism() {
		for (NetworkBlockContainer c : this.containers) {
			if (c != null)
				c.clearDynamism();
		}

		if (this.listener != null)
			this.listener.clearDynamism();
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		checkBounds(x, y, z);

		NetworkBlockContainer container = this.containers[y >> 4];
		if (container == null) {
			this.chunk.bindOrCreateContainer(y >> 4);
			container = this.containers[y >> 4];
		}

		container.set(x & 15, y & 15, z & 15, (IBlockState) block);
		if (this.listener != null)
			this.listener.addChange(x & 15, y, z & 15);
		this.saved = false;
		return true;
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		checkBounds(x, y, z);

		x &= 15;
		z &= 15;

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
