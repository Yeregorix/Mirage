/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.impl.internal.InternalBlockState;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.util.BlockUtil;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.fluid.FluidState;
import org.spongepowered.api.world.volume.stream.StreamOptions;
import org.spongepowered.api.world.volume.stream.VolumeStream;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.storage.SpongeChunkLayout;
import org.spongepowered.math.vector.Vector3i;

import java.util.Random;

import static net.smoofyuniverse.mirage.util.BlockUtil.AIR;
import static org.spongepowered.math.GenericMath.clamp;

/**
 * Represents a chunk viewed for the network (aka online players)
 */
public class NetworkChunk implements ChunkView {
	private final InternalChunk chunk;

	private final NetworkWorld world;
	private final Vector3i position, blockMin, blockMax;
	public final int x, z;
	private final boolean dynamismEnabled;
	private final long seed;

	private final NetworkSection[] sections = new NetworkSection[16];
	private final Random random = new Random();
	private State state = State.DEOBFUSCATED;
	private ChunkChangeListener listener;

	public NetworkChunk(InternalChunk chunk, NetworkWorld world) {
		this.chunk = chunk;
		this.world = world;
		this.position = chunk.chunkPosition();
		this.blockMin = chunk.min();
		this.blockMax = chunk.max();
		this.dynamismEnabled = world.isDynamismEnabled();
		this.x = this.position.x();
		this.z = this.position.z();

		long wSeed = world.config().obfuscationSeed;
		this.random.setSeed(wSeed);
		long k = this.random.nextLong() | 1L;
		long l = this.random.nextLong() | 1L;
		this.seed = (long) this.x * k + (long) this.z * l ^ wSeed;
	}

	public void captureSection(int y) {
		captureSection(this.chunk.getSections()[y]);
	}

	public void captureSection(LevelChunkSection section) {
		if (section != null)
			captureSection(((InternalSection) section).view());
	}

	private void captureSection(NetworkSection section) {
		if (section == null)
			return;

		int y = section.getY();
		NetworkSection old = this.sections[y];
		if (old == section)
			return;

		if (old != null && !old.isEmpty())
			Mirage.LOGGER.warn("A new section has been captured and will replace a non-empty one (" + this.x + ", " + y + ", " + this.z + ").");

		this.sections[y] = section;
	}

	public ChunkChangeListener getListener() {
		return this.listener;
	}

	public boolean isDirty() {
		for (NetworkSection container : this.sections) {
			if (container != null && container.dirty)
				return true;
		}
		return false;
	}

	public void saveToCacheLater() {
		if (shouldSave()) {
			long time = ((ServerLevel) this.world.storage()).getGameTime();

			if (this.world.useCache()) {
				CompoundTag tag = serialize();
				tag.putLong("LastUpdate", time);

				this.world.addPendingSave(this.x, this.z, tag);
			}

			this.chunk.setCacheTime(time);
			((LevelChunk) this.chunk).markUnsaved();
			clearDirty();
		}
	}

	public void setListener(ChunkChangeListener listener) {
		this.listener = listener;
	}

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("xPos", this.x);
		tag.putInt("zPos", this.z);

		ListTag sectionsTag = new ListTag();
		for (NetworkSection section : this.sections) {
			if (section != null)
				sectionsTag.add(section.serialize());
		}
		tag.put("Sections", sectionsTag);
		return tag;
	}

	public boolean shouldSave() {
		return this.state == State.OBFUSCATED && isDirty();
	}

	private void clearDirty() {
		for (NetworkSection container : this.sections) {
			if (container != null)
				container.dirty = false;
		}
	}

	public void saveToCacheNow() {
		if (shouldSave()) {
			long time = ((ServerLevel) this.world.storage()).getGameTime();

			if (this.world.useCache()) {
				CompoundTag tag = serialize();
				tag.putLong("LastUpdate", time);

				this.world.removePendingSave(this.x, this.z);
				this.world.saveToCache(this.x, this.z, tag);
			}

			this.chunk.setCacheTime(time);
			((LevelChunk) this.chunk).markUnsaved();
			clearDirty();
		}
	}

	public void loadFromCacheNow() {
		if (this.world.useCache()) {
			CompoundTag tag = this.world.readFromCache(this.x, this.z);
			if (tag != null && tag.getLong("LastUpdate") == this.chunk.getCacheTime()) {
				this.world.removePendingSave(this.x, this.z);
				deserialize(tag);

				this.state = State.OBFUSCATED;
				clearDirty();
			}
		}
	}

	public void deserialize(CompoundTag tag) {
		captureSections();

		for (Tag t : tag.getList("Sections", 10)) {
			CompoundTag sectionTag = (CompoundTag) t;
			NetworkSection container = this.sections[sectionTag.getByte("Y")];
			if (container != null)
				container.deserialize(sectionTag);
		}
	}

	public void captureSections() {
		for (LevelChunkSection section : this.chunk.getSections())
			captureSection(section);
	}

	@Override
	public InternalChunk storage() {
		return this.chunk;
	}

	@Override
	public State state() {
		return this.state;
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

		captureSections();

		boolean requireNeighbors = false;
		for (ConfiguredModifier mod : this.world.config().modifiers) {
			if (mod.modifier.requireNeighborsLoaded()) {
				requireNeighbors = true;
				break;
			}
		}

		if (requireNeighbors && !areNeighborsLoaded()) {
			this.state = State.OBFUSCATION_REQUESTED;
		} else {
			this.random.setSeed(this.seed);

			for (ConfiguredModifier mod : this.world.config().modifiers) {
				try {
					mod.modifier.modify(this, this.random, mod.config);
				} catch (Exception ex) {
					Mirage.LOGGER.error("Modifier " + ChunkModifier.REGISTRY_TYPE.get().valueKey(mod.modifier) + " has thrown an exception while modifying a network chunk", ex);
				}
			}

			this.state = State.OBFUSCATED;
		}
	}

	@Override
	public void deobfuscate() {
		if (this.state == State.DEOBFUSCATED)
			return;

		for (NetworkSection section : this.sections) {
			if (section != null) {
				section.clearDynamism();
				section.deobfuscate(this.listener);
			}
		}

		if (this.listener != null)
			this.listener.clearDynamism();

		this.state = State.DEOBFUSCATED;
	}

	@Override
	public boolean isDynamismEnabled() {
		return this.dynamismEnabled;
	}

	@Override
	public void clearDynamism() {
		for (NetworkSection section : this.sections) {
			if (section != null)
				section.clearDynamism();
		}

		if (this.listener != null)
			this.listener.clearDynamism();
	}

	@Override
	public NetworkWorld world() {
		return this.world;
	}

	@Override
	public void reobfuscate() {
		if (this.state != State.OBFUSCATED)
			throw new IllegalStateException("Chunk must be obfuscated");

		deobfuscate();
		obfuscate();
	}

	@Override
	public void setDynamism(int x, int y, int z, int distance) {
		checkBlockPosition(x, y, z);
		if (!this.dynamismEnabled)
			return;

		distance = clamp(distance, 0, 10);
		requireSection(y >> 4).setDynamism(x & 15, y & 15, z & 15, distance);
		if (this.listener != null)
			this.listener.updateDynamism(x & 15, y, z & 15, distance);
	}

	private NetworkSection requireSection(int y) {
		NetworkSection container = this.sections[y];
		if (container == null) {
			captureSection(this.chunk.requireSection(y));
			container = this.sections[y];
		}
		return container;
	}

	@Override
	public int dynamism(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		if (!this.dynamismEnabled)
			return 0;

		NetworkSection section = this.sections[y >> 4];
		return section == null ? 0 : section.getDynamism(x & 15, y & 15, z & 15);
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		if (this.state == State.DEOBFUSCATED)
			return false;

		NetworkSection section = this.sections[y >> 4];
		return section != null && section.deobfuscate(this.listener, x & 15, y & 15, z & 15);
	}

	@Override
	public Vector3i chunkPosition() {
		return this.position;
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
			NetworkChunk section = this.world.chunk(this.x + 1, this.z);
			if (section == null || !section.isOpaque(0, y, z))
				return true;
		} else if (!isOpaque(x + 1, y, z))
			return true;

		// x - 1
		if (x == 0) {
			NetworkChunk section = this.world.chunk(this.x - 1, this.z);
			if (section == null || !section.isOpaque(15, y, z))
				return true;
		} else if (!isOpaque(x - 1, y, z))
			return true;

		// z + 1
		if (z == 15) {
			NetworkChunk section = this.world.chunk(this.x, this.z + 1);
			if (section == null || !section.isOpaque(x, y, 0))
				return true;
		} else if (!isOpaque(x, y, z + 1))
			return true;

		// z - 1
		if (z == 0) {
			NetworkChunk section = this.world.chunk(this.x, this.z - 1);
			if (section == null || !section.isOpaque(x, y, 15))
				return true;
		} else if (!isOpaque(x, y, z - 1))
			return true;

		return false;
	}

	@Override
	public void deobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (this.state != State.DEOBFUSCATED)
			deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		NetworkSection section = this.sections[y >> 4];
		return section != null && ((InternalBlockState) section.getBlockState(x & 15, y & 15, z & 15)).isOpaque();
	}

	@Override
	public Vector3i min() {
		return this.blockMin;
	}

	@Override
	public Vector3i max() {
		return this.blockMax;
	}

	@Override
	public Vector3i size() {
		return SpongeChunkLayout.CHUNK_SIZE;
	}

	@Override
	public boolean contains(int x, int y, int z) {
		return VecHelper.inBounds(x, y, z, this.blockMin, this.blockMax);
	}

	@Override
	public boolean isAreaAvailable(int x, int y, int z) {
		return contains(x, y, z);
	}

	@Override
	public boolean areNeighborsLoaded() {
		return this.world.isChunkLoaded(this.x + 1, this.z) && this.world.isChunkLoaded(this.x, this.z + 1) && this.world.isChunkLoaded(this.x - 1, this.z) && this.world.isChunkLoaded(this.x, this.z - 1);
	}

	protected void reobfuscate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
		Vector3i min = new Vector3i(minX, minY, minZ), max = new Vector3i(maxX, maxY, maxZ);

		for (ConfiguredModifier mod : this.world.config().modifiers) {
			try {
				mod.modifier.modify(this, min, max, this.random, mod.config);
			} catch (Exception ex) {
				Mirage.LOGGER.error("Modifier " + ChunkModifier.REGISTRY_TYPE.get().valueKey(mod.modifier) + " has thrown an exception while (re)modifying a part of a network chunk", ex);
			}
		}
	}

	protected void deobfuscate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		for (int y = minY; y <= maxY; y++) {
			NetworkSection container = this.sections[y >> 4];
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
	public VolumeStream<Mutable, BlockState> blockStateStream(Vector3i min, Vector3i max, StreamOptions options) {
		return BlockUtil.blockStateStream(this, min, max, options);
	}

	@Override
	public BlockState block(int x, int y, int z) {
		checkBlockPosition(x, y, z);
		NetworkSection section = this.sections[y >> 4];
		return section == null ? AIR : (BlockState) section.getBlockState(x & 15, y & 15, z & 15);
	}

	@Override
	public FluidState fluid(int x, int y, int z) {
		return block(x, y, z).fluidState();
	}

	@Override
	public int highestYAt(int x, int z) {
		for (int i = 15; i >= 0; i--) {
			NetworkSection section = this.sections[i];
			if (section != null && !section.isEmpty()) {
				for (int y = 15; y >= 0; y--) {
					if (!section.getBlockState(x, y, z).isAir())
						return (i << 4) + y + 1;
				}
			}
		}
		return 0;
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		checkBlockPosition(x, y, z);

		requireSection(y >> 4).setBlockState(x & 15, y & 15, z & 15, (net.minecraft.world.level.block.state.BlockState) block);
		if (this.listener != null)
			this.listener.addChange(x & 15, y, z & 15);
		return true;
	}

	@Override
	public boolean removeBlock(int x, int y, int z) {
		return setBlock(x, y, z, AIR);
	}


	public static long asLong(int x, int z) {
		return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
	}
}
