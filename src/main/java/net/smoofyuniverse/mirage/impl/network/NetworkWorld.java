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
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.api.cache.Signature;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.api.volume.WorldView;
import net.smoofyuniverse.mirage.config.world.MainConfig;
import net.smoofyuniverse.mirage.config.world.WorldConfig;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.cache.ChunkSnapshot;
import net.smoofyuniverse.mirage.impl.network.cache.NetworkRegionCache;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.world.GeneratorTypes;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.util.gen.ArrayImmutableBlockBuffer;
import org.spongepowered.common.util.gen.ArrayMutableBlockBuffer;
import org.spongepowered.common.world.extent.ExtentBufferUtil;
import org.spongepowered.common.world.extent.MutableBlockViewDownsize;
import org.spongepowered.common.world.extent.MutableBlockViewTransform;
import org.spongepowered.common.world.extent.UnmodifiableBlockVolumeWrapper;
import org.spongepowered.common.world.extent.worker.SpongeMutableBlockVolumeWorker;
import org.spongepowered.common.world.schematic.GlobalPalette;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.smoofyuniverse.mirage.impl.network.NetworkChunk.asLong;

/**
 * Represents the world viewed for the network (akka online players)
 */
public class NetworkWorld implements WorldView {
	private final Long2ObjectMap<ChunkSnapshot> chunksToSave = new Long2ObjectOpenHashMap<>();
	private final Vector3i blockMin, blockMax, blockSize;
	private final InternalWorld world;

	private NetworkRegionCache cache;
	private WorldConfig config;
	private Signature signature;
	private boolean enabled, dynamismEnabled;

	private final Random random = new Random();

	public NetworkWorld(InternalWorld world) {
		this.world = world;
		this.blockMin = world.getBlockMin();
		this.blockMax = world.getBlockMax();
		this.blockSize = world.getBlockSize();
	}

	public void loadConfig() {
		if (this.config != null)
			throw new IllegalStateException("Config already loaded");

		Mirage.LOGGER.info("Loading configuration for world " + getName() + " ..");
		try {
			_loadConfig();
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to load configuration for world " + getName(), e);
		}

		if (this.config == null)
			this.config = WorldConfig.DISABLED;

		this.enabled = this.config.main.enabled;
		this.dynamismEnabled = this.enabled && this.config.main.dynamism;
	}

	private void _loadConfig() {
		WorldConfig cfg = Mirage.get().getConfig((World) this.world);
		MainConfig.Immutable main = cfg.main;
		List<ConfiguredModifier> modifiers = cfg.modifiers;

		WorldProperties properties = this.world.getProperties();
		if (main.enabled && properties.getGeneratorType() == GeneratorTypes.DEBUG) {
			Mirage.LOGGER.warn("Obfuscation is not available in a debug world. Obfuscation will be disabled.");
			main = main.disable();
		}

		if (main.enabled) {
			ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();

			for (ConfiguredModifier mod : modifiers) {
				if (!mod.modifier.isCompatible(properties)) {
					Mirage.LOGGER.warn("Modifier " + mod.modifier.getId() + " is not compatible with this world. This modifier will be ignored.");
					continue;
				}

				mods.add(mod);
			}
			modifiers = mods.build();
		}

		if (main.enabled && modifiers.isEmpty()) {
			Mirage.LOGGER.info("No valid modifier was found. Obfuscation will be disabled.");
			main = main.disable();
		}

		long seed = 0;

		if (main.enabled && main.cache) {
			Signature.Builder b = Signature.builder();
			for (ConfiguredModifier mod : modifiers)
				b.append(mod.modifier);
			String cacheName = this.world.getUniqueId() + "/" + b.build();

			try {
				this.cache = new NetworkRegionCache(cacheName);
				this.cache.load();
				seed = this.cache.getSeed();

				b = Signature.builder().append(seed).append(main.dynamism);
				for (ConfiguredModifier mod : modifiers)
					mod.modifier.appendSignature(b, mod.config);
				this.signature = b.build();
			} catch (Exception e) {
				Mirage.LOGGER.warn("Failed to load cache " + cacheName + "/. Cache will be disabled.", e);
				this.cache = null;
			}
		}

		if (this.cache == null)
			seed = ThreadLocalRandom.current().nextLong();

		this.config = new WorldConfig(main, modifiers, seed);
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	public boolean useCache() {
		return this.cache != null;
	}

	public void addPendingSave(int x, int z, ChunkSnapshot chunk) {
		if (this.cache == null)
			return;

		synchronized (this.chunksToSave) {
			this.chunksToSave.put(asLong(x, z), chunk);
		}
	}

	public void removePendingSave(int x, int z) {
		if (this.cache == null)
			return;

		synchronized (this.chunksToSave) {
			this.chunksToSave.remove(asLong(x, z));
		}
	}

	public void savePendingChunk(int x, int z) {
		if (this.cache == null)
			return;

		ChunkSnapshot chunk;
		synchronized (this.chunksToSave) {
			chunk = this.chunksToSave.remove(asLong(x, z));
		}
		if (chunk != null)
			saveToCache(x, z, chunk);
	}

	public void saveToCache(int x, int z, ChunkSnapshot chunk) {
		if (this.cache == null)
			throw new IllegalStateException();

		MirageTimings.WRITING_CACHE.startTimingIfSync();

		try (DataOutputStream out = this.cache.getChunkOutputStream(x, z)) {
			this.signature.write(out);
			chunk.write(out);
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to save chunk data " + x + " " + z + " to cache in world " + this.world.getName() + ".", e);
		}

		// Fix a sponge issue with Timings
		if (SpongeImpl.getServer().isCallingFromMinecraftThread())
			MirageTimings.WRITING_CACHE.stopTimingIfSync();
	}

	@Nullable
	public ChunkSnapshot readFromCache(int x, int z) {
		if (this.cache == null)
			throw new IllegalStateException();

		MirageTimings.READING_CACHE.startTimingIfSync();

		try (DataInputStream in = this.cache.getChunkInputStream(x, z)) {
			if (in != null && Signature.read(in).equals(this.signature))
				return new ChunkSnapshot().read(in);
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to read chunk data " + x + " " + z + " from cache in world " + this.world.getName() + ".", e);
		} finally {
			MirageTimings.READING_CACHE.stopTimingIfSync();
		}

		return null;
	}

	@Override
	public InternalWorld getStorage() {
		return this.world;
	}

	@Override
	public boolean isDynamismEnabled() {
		return this.dynamismEnabled;
	}

	@Override
	public void setDynamism(int x, int y, int z, int distance) {
		if (this.dynamismEnabled) {
			NetworkChunk chunk = getChunk(x >> 4, z >> 4);
			if (chunk != null)
				chunk.setDynamism(x, y, z, distance);
		}
	}

	@Override
	public int getDynamism(int x, int y, int z) {
		if (!this.dynamismEnabled)
			return 0;

		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk == null ? 0 : chunk.getDynamism(x, y, z);
	}

	@Override
	public String getName() {
		return this.world.getName();
	}

	@Override
	public WorldProperties getProperties() {
		return this.world.getProperties();
	}

	@Override
	public WorldConfig getConfig() {
		if (this.config == null)
			throw new IllegalStateException("Config not loaded");
		return this.config;
	}

	@Override
	public boolean isOChunkLoaded(int x, int y, int z) {
		return isChunkLoaded(x, z);
	}

	@Override
	public Optional<ChunkView> getOChunk(int x, int y, int z) {
		return Optional.ofNullable(getChunk(x, z));
	}

	@Override
	public Optional<ChunkView> getOChunkAt(int x, int y, int z) {
		return getOChunk(x >> 4, 0, z >> 4);
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.deobfuscate(x, y, z);
	}

	@Override
	public void deobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (!this.enabled)
			return;

		int minChunkX = minX >> 4, minChunkZ = minZ >> 4, maxChunkX = maxX >> 4, maxChunkZ = maxZ >> 4;
		if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {
			NetworkChunk chunk = getChunk(minChunkX, minChunkZ);
			if (chunk == null) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be loaded");
			}

			if (chunk.getState() != State.DEOBFUSCATED)
				chunk.deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
			return;
		}

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (getChunk(chunkX, chunkZ) == null) {
					if (silentFail)
						return;
					throw new IllegalStateException("Chunks must be loaded");
				}
			}
		}

		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private void deobfuscate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int minChunkX = minX >> 4, minChunkZ = minZ >> 4, maxChunkX = maxX >> 4, maxChunkZ = maxZ >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				NetworkChunk chunk = getChunk(chunkX, chunkZ);

				if (chunk.getState() != State.DEOBFUSCATED) {
					int chunkMinX = chunkX << 4, chunkMinZ = chunkZ << 4;
					chunk.deobfuscate(max(minX, chunkMinX), minY, max(minZ, chunkMinZ), min(maxX, chunkMinX + 15), maxY, min(maxZ, chunkMinZ + 15));
				}
			}
		}
	}

	@Override
	public void reobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (!this.enabled)
			return;

		int minChunkX = minX >> 4, minChunkZ = minZ >> 4, maxChunkX = maxX >> 4, maxChunkZ = maxZ >> 4;
		if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {
			NetworkChunk chunk = getChunk(minChunkX, minChunkZ);
			if (chunk == null) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be loaded");
			}
			if (chunk.getState() != State.OBFUSCATED) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be obfuscated");
			}

			chunk.reobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
			return;
		}

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				NetworkChunk chunk = getChunk(chunkX, chunkZ);
				if (chunk == null) {
					if (silentFail)
						return;
					throw new IllegalStateException("Chunks must be loaded");
				}
				if (chunk.getState() != State.OBFUSCATED) {
					if (silentFail)
						return;
					throw new IllegalStateException("Chunks must be obfuscated");
				}
			}
		}

		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
		Vector3i min = new Vector3i(minX, minY, minZ), max = new Vector3i(maxX, maxY, maxZ);

		MirageTimings.REOBFUSCATION.startTiming();

		for (ConfiguredModifier mod : this.config.modifiers) {
			Timing timing = mod.modifier.getTiming();
			timing.startTiming();

			try {
				mod.modifier.modify(this, min, max, this.random, mod.config);
			} catch (Exception ex) {
				Mirage.LOGGER.error("Modifier " + mod.modifier.getId() + " has thrown an exception while (re)modifying a part of a network world", ex);
			}

			timing.stopTiming();
		}

		MirageTimings.REOBFUSCATION.stopTiming();
	}

	@Nullable
	public NetworkChunk getChunk(int x, int z) {
		NetworkChunk chunk = getChunkPassively(x, z);
		if (chunk != null)
			chunk.getStorage().markActive();
		return chunk;
	}

	@Nullable
	public NetworkChunk getChunkPassively(int x, int z) {
		InternalChunk chunk = this.world.getChunkPassively(x, z);
		return chunk != null && chunk.isViewAvailable() ? chunk.getView() : null;
	}

	@Override
	public Collection<NetworkChunk> getLoadedOChunks() {
		if (!this.enabled)
			return ImmutableList.of();

		ImmutableList.Builder<NetworkChunk> b = ImmutableList.builder();
		for (InternalChunk c : this.world.getLoadedOChunks()) {
			if (c.isViewAvailable())
				b.add(c.getView());
		}
		return b.build();
	}

	public boolean isChunkLoaded(int x, int z) {
		return getChunkPassively(x, z) != null;
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isExposed(x, y, z);
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isOpaque(x, y, z);
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.setBlock(x, y, z, block);
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
	public Vector3i getBlockMin() {
		return this.blockMin;
	}

	@Override
	public Vector3i getBlockMax() {
		return this.blockMax;
	}

	@Override
	public Vector3i getBlockSize() {
		return this.blockSize;
	}

	@Override
	public boolean containsBlock(int x, int y, int z) {
		return VecHelper.inBounds(x, y, z, this.blockMin, this.blockMax);
	}

	@Override
	public BlockState getBlock(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk == null ? BlockTypes.AIR.getDefaultState() : chunk.getBlock(x, y, z);
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
				return new ArrayMutableBlockBuffer(GlobalPalette.getBlockPalette(), this.blockMin, this.blockMax, ExtentBufferUtil.copyToArray(this, this.blockMin, this.blockMax, this.blockSize));
			case THREAD_SAFE:
			default:
				throw new UnsupportedOperationException(type.name());
		}
	}

	@Override
	public ImmutableBlockVolume getImmutableBlockCopy() {
		return ArrayImmutableBlockBuffer.newWithoutArrayClone(GlobalPalette.getBlockPalette(), this.blockMin, this.blockMax, ExtentBufferUtil.copyToArray(this, this.blockMin, this.blockMax, this.blockSize));
	}

	@Override
	public UUID getUniqueId() {
		return this.world.getUniqueId();
	}
}
