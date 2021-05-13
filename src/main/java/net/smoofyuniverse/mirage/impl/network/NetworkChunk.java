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

import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockContainer;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.network.cache.BlockContainerSnapshot;
import net.smoofyuniverse.mirage.impl.network.cache.ChunkSnapshot;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicChunk;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.DiscreteTransform3;
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

import static net.smoofyuniverse.mirage.util.MathUtil.*;

/**
 * Represents a chunk viewed for the network (akka online players)
 */
public class NetworkChunk implements ChunkView {
	public static final int maxDynamismDistance2 = squared(160);

	private final InternalChunk chunk;

	private final NetworkWorld world;
	private final Vector3i position, blockMin, blockMax;
	public final int x, z;
	private final boolean dynamismEnabled;
	private final long seed;

	private final NetworkBlockContainer[] containers = new NetworkBlockContainer[16];
	private final Random random = new Random();
	private State state = State.DEOBFUSCATED;
	private ChunkChangeListener listener;

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

	public void captureContainer(ExtendedBlockStorage storage) {
		if (storage != null)
			captureContainer(((InternalBlockContainer) storage.getData()).getNetworkBlockContainer());
	}

	public void captureContainer(NetworkBlockContainer container) {
		if (container == null)
			return;

		int index = container.getY() >> 4;
		NetworkBlockContainer old = this.containers[index];
		if (old == container)
			return;

		if (old != null && !old.isEmpty())
			Mirage.LOGGER.warn("A new container has been captured and will replace a non-empty one (" + this.x + ", " + index + ", " + this.z + ").");

		this.containers[index] = container;
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
			clearDirty();
		}
	}

	public Optional<ChunkChangeListener> getListener() {
		return Optional.ofNullable(this.listener);
	}

	public void setListener(ChunkChangeListener listener) {
		this.listener = listener;
	}

	@Override
	public State getState() {
		return this.state;
	}

	public boolean shouldSave() {
		return this.state == State.OBFUSCATED && isDirty();
	}

	private void clearDirty() {
		for (NetworkBlockContainer container : this.containers) {
			if (container != null)
				container.dirty = false;
		}
	}

	public boolean isDirty() {
		for (NetworkBlockContainer container : this.containers) {
			if (container != null && container.dirty)
				return true;
		}
		return false;
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
			clearDirty();
		}
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

	public void loadFromCacheNow() {
		if (this.world.useCache()) {
			ChunkSnapshot chunk = this.world.readFromCache(this.x, this.z);
			if (chunk != null && chunk.getDate() == this.chunk.getValidCacheDate()) {
				this.world.removePendingSave(this.x, this.z);
				load(chunk);

				this.state = State.OBFUSCATED;
				clearDirty();
			}
		}
	}

	public void load(ChunkSnapshot in) {
		this.chunk.captureContainers();

		for (BlockContainerSnapshot data : in.getContainers()) {
			NetworkBlockContainer container = this.containers[data.getSection()];
			if (container != null)
				container.load(data);
		}
	}

	@Override
	public void reobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (this.state != State.OBFUSCATED) {
			if (silentFail)
				return;
			throw new IllegalStateException("Chunk must be obfuscated");
		}

		reobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public void obfuscate() {
		if (this.state == State.OBFUSCATED)
			return;

		this.chunk.captureContainers();

		MirageTimings.OBFUSCATION.startTiming();

		boolean requireNeighbors = false;
		for (ConfiguredModifier mod : this.world.getConfig().modifiers) {
			if (mod.modifier.requireNeighborsLoaded()) {
				requireNeighbors = true;
				break;
			}
		}

		if (requireNeighbors && !areNeighborsLoaded()) {
			this.state = State.OBFUSCATION_REQUESTED;
		} else {
			this.random.setSeed(this.seed);

			for (ConfiguredModifier mod : this.world.getConfig().modifiers) {
				Timing timing = mod.modifier.getTiming();
				timing.startTiming();

				try {
					mod.modifier.modify(this, this.random, mod.config);
				} catch (Exception ex) {
					Mirage.LOGGER.error("Modifier " + mod.modifier.getId() + " has thrown an exception while modifying a network chunk", ex);
				}

				timing.stopTiming();
			}

			this.state = State.OBFUSCATED;
		}

		MirageTimings.OBFUSCATION.stopTiming();
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
		checkBlockPosition(x, y, z);
		if (!this.dynamismEnabled)
			return;

		distance = clamp(distance, 0, 10);
		requireContainer(y >> 4).setDynamism(x & 15, y & 15, z & 15, distance);
		if (this.listener != null)
			this.listener.updateDynamism(x & 15, y, z & 15, distance);
	}

	@Override
	public void deobfuscate() {
		if (this.state == State.DEOBFUSCATED)
			return;

		MirageTimings.DEOBFUSCATION.startTiming();

		for (NetworkBlockContainer c : this.containers) {
			if (c != null) {
				c.clearDynamism();
				c.deobfuscate(this.listener);
			}
		}

		if (this.listener != null)
			this.listener.clearDynamism();

		this.state = State.DEOBFUSCATED;

		MirageTimings.DEOBFUSCATION.stopTiming();
	}

	@Override
	public void reobfuscate() {
		if (this.state != State.OBFUSCATED)
			throw new IllegalStateException("Chunk must be obfuscated");

		deobfuscate();
		obfuscate();
	}

	public void collectDynamicPositions(DynamicChunk chunk) {
		if (!this.dynamismEnabled)
			return;

		Vector3i relCenter = chunk.getRelativeCenter();
		int cX = relCenter.getX(), cY = relCenter.getY(), cZ = relCenter.getZ();

		int minXZDistance2 = lengthSquared(clamp(cX, 0, 15) - cX, clamp(cZ, 0, 15) - cZ);
		if (minXZDistance2 + squared(clamp(cY, 0, 255) - cY) > maxDynamismDistance2)
			return;

		for (NetworkBlockContainer c : this.containers) {
			if (c != null) {
				int relY = cY - c.getY();

				int d2 = minXZDistance2 + squared(clamp(relY, 0, 15) - relY);
				if (d2 > maxDynamismDistance2 || d2 > squared(c.getMaxDynamism()) << 8)
					continue;

				c.collectDynamicPositions(chunk);
			}
		}

		if (this.listener != null)
			this.listener.markDirty();
	}

	@Override
	public NetworkWorld getWorld() {
		return this.world;
	}

	@Override
	public Vector3i getPosition() {
		return this.position;
	}

	private NetworkBlockContainer requireContainer(int index) {
		NetworkBlockContainer container = this.containers[index];
		if (container == null) {
			this.chunk.requireContainer(index);
			container = this.containers[index];
		}
		return container;
	}

	@Override
	public int getDynamism(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		if (!this.dynamismEnabled)
			return 0;

		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null ? 0 : c.getDynamism(x & 15, y & 15, z & 15);
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		checkBlockPosition(x, y, z);

		x &= 15;
		z &= 15;

		// y + 1
		if (y == 255 || !isOpaque(x, y + 1, z))
			return true;

		// y - 1
		if (y == 0 || !isOpaque(x, y - 1, z))
			return true;

		// x + 1
		if (x == 15) {
			NetworkChunk c = this.world.getChunk(this.x + 1, this.z);
			if (c == null || !c.isOpaque(0, y, z))
				return true;
		} else if (!isOpaque(x + 1, y, z))
			return true;

		// x - 1
		if (x == 0) {
			NetworkChunk c = this.world.getChunk(this.x - 1, this.z);
			if (c == null || !c.isOpaque(15, y, z))
				return true;
		} else if (!isOpaque(x - 1, y, z))
			return true;

		// z + 1
		if (z == 15) {
			NetworkChunk c = this.world.getChunk(this.x, this.z + 1);
			if (c == null || !c.isOpaque(x, y, 0))
				return true;
		} else if (!isOpaque(x, y, z + 1))
			return true;

		// z - 1
		if (z == 0) {
			NetworkChunk c = this.world.getChunk(this.x, this.z - 1);
			if (c == null || !c.isOpaque(x, y, 15))
				return true;
		} else if (!isOpaque(x, y, z - 1))
			return true;

		return false;
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		NetworkBlockContainer c = this.containers[y >> 4];
		return c != null && ((InternalBlockState) c.get(x & 15, y & 15, z & 15)).isOpaque();
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		if (this.state == State.DEOBFUSCATED)
			return false;

		NetworkBlockContainer container = this.containers[y >> 4];
		return container != null && container.deobfuscate(this.listener, x & 15, y & 15, z & 15);
	}

	@Override
	public void deobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (this.state != State.DEOBFUSCATED)
			deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
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
				return new ArrayMutableBlockBuffer(GlobalPalette.getBlockPalette(), getBlockMin(), getBlockSize(), ExtentBufferUtil.copyToArray(this, getBlockMin(), getBlockMax(), getBlockSize()));
			case THREAD_SAFE:
			default:
				throw new UnsupportedOperationException(type.name());
		}
	}

	@Override
	public ImmutableBlockVolume getImmutableBlockCopy() {
		return ArrayImmutableBlockBuffer.newWithoutArrayClone(GlobalPalette.getBlockPalette(), getBlockMin(), getBlockSize(), ExtentBufferUtil.copyToArray(this, getBlockMin(), getBlockMax(), getBlockSize()));
	}

	protected void deobfuscate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		for (int y = minY; y <= maxY; y++) {
			NetworkBlockContainer container = this.containers[y >> 4];
			if (container == null)
				continue;

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					container.deobfuscate(this.listener, x & 15, y & 15, z & 15);
				}
			}
		}
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



	protected void reobfuscate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
		Vector3i min = new Vector3i(minX, minY, minZ), max = new Vector3i(maxX, maxY, maxZ);

		MirageTimings.REOBFUSCATION.startTiming();

		for (ConfiguredModifier mod : this.world.getConfig().modifiers) {
			Timing timing = mod.modifier.getTiming();
			timing.startTiming();

			try {
				mod.modifier.modify(this, min, max, this.random, mod.config);
			} catch (Exception ex) {
				Mirage.LOGGER.error("Modifier " + mod.modifier.getId() + " has thrown an exception while (re)modifying a part of a network chunk", ex);
			}

			timing.stopTiming();
		}

		MirageTimings.REOBFUSCATION.stopTiming();
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
	public BlockState getBlock(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		NetworkBlockContainer c = this.containers[y >> 4];
		return c == null ? BlockTypes.AIR.getDefaultState() : (BlockState) c.get(x & 15, y & 15, z & 15);
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		checkBlockPosition(x, y, z);

		requireContainer(y >> 4).set(x & 15, y & 15, z & 15, (IBlockState) block);
		if (this.listener != null)
			this.listener.addChange(x & 15, y, z & 15);
		return true;
	}

	public static long asLong(int x, int z) {
		return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
	}
}
