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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.api.volume.BlockStorage;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.api.volume.WorldView;
import net.smoofyuniverse.mirage.config.world.MainConfig.Resolved;
import net.smoofyuniverse.mirage.config.world.WorldConfig;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.cache.NetworkRegionCache;
import net.smoofyuniverse.mirage.util.BlockUtil;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.fluid.FluidState;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.server.storage.ServerWorldProperties;
import org.spongepowered.api.world.volume.stream.StreamOptions;
import org.spongepowered.api.world.volume.stream.VolumeStream;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.math.vector.Vector3i;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static net.smoofyuniverse.mirage.impl.network.NetworkChunk.asLong;
import static net.smoofyuniverse.mirage.util.BlockUtil.AIR;
import static net.smoofyuniverse.mirage.util.BlockUtil.NO_FLUID;

/**
 * Represents the world viewed for the network (aka online players)
 */
public class NetworkWorld implements WorldView {
	private final Long2ObjectMap<CompoundTag> chunksToSave = new Long2ObjectOpenHashMap<>();
	private final Vector3i blockMin, blockMax, blockSize;
	private final InternalWorld world;

	private NetworkRegionCache cache;
	private WorldConfig config;
	private Signature signature;
	private boolean enabled, dynamismEnabled;

	private final Random random = new Random();

	public NetworkWorld(InternalWorld world) {
		this.world = world;
		this.blockMin = world.min();
		this.blockMax = world.max();
		this.blockSize = world.size();
	}

	public void loadConfig() {
		if (this.config != null)
			throw new IllegalStateException("Config already loaded");

		Mirage.LOGGER.info("Loading configuration for world " + key() + " ...");
		try {
			_loadConfig();
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to load configuration for world " + key(), e);
		}

		if (this.config == null)
			this.config = WorldConfig.DISABLED;

		this.enabled = this.config.main.enabled;
		this.dynamismEnabled = this.enabled && this.config.main.dynamism;
	}

	@Override
	public ResourceKey key() {
		return this.world.key();
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	public boolean useCache() {
		return this.cache != null;
	}

	private void _loadConfig() {
		WorldConfig cfg = Mirage.get().getConfig((ServerWorld) this.world);
		Resolved main = cfg.main;
		List<ConfiguredModifier> modifiers = cfg.modifiers;

		if (main.enabled && ((Level) this.world).isDebug()) {
			Mirage.LOGGER.warn("Obfuscation is not available in a debug world. Obfuscation will be disabled.");
			main = main.disable();
		}

		ServerWorldProperties properties = this.world.properties();
		Registry<ChunkModifier> modifierRegistry = ChunkModifier.REGISTRY_TYPE.get();

		if (main.enabled) {
			ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();

			for (ConfiguredModifier mod : modifiers) {
				if (!mod.modifier.isCompatible(properties)) {
					Mirage.LOGGER.warn("Modifier " + modifierRegistry.valueKey(mod.modifier) + " is not compatible with this world. This modifier will be ignored.");
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
				b.append(modifierRegistry.valueKey(mod.modifier));
			String cacheName = this.world.uniqueId() + "/" + b.build();

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

	public void removePendingSave(int x, int z) {
		if (this.cache == null)
			return;

		synchronized (this.chunksToSave) {
			this.chunksToSave.remove(asLong(x, z));
		}
	}

	@Override
	public ServerWorldProperties properties() {
		return this.world.properties();
	}

	@Override
	public boolean isOpaqueChunkLoaded(int x, int y, int z) {
		return isChunkLoaded(x, z);
	}

	@Override
	public Optional<ChunkView> opaqueChunk(int x, int y, int z) {
		return Optional.ofNullable(chunk(x, z));
	}

	@Override
	public Optional<ChunkView> opaqueChunkAt(int x, int y, int z) {
		return opaqueChunk(x >> 4, 0, z >> 4);
	}

	@Override
	public boolean isDynamismEnabled() {
		return this.dynamismEnabled;
	}

	@Override
	public Stream<NetworkChunk> loadedOpaqueChunks() {
		if (!this.enabled)
			return Stream.empty();

		return this.world.loadedOpaqueChunks().filter(BlockStorage::isViewAvailable).map(InternalChunk::view);
	}

	public boolean isChunkLoaded(int x, int z) {
		return chunk(x, z) != null;
	}

	public void addPendingSave(int x, int z, CompoundTag chunk) {
		if (this.cache == null)
			return;

		synchronized (this.chunksToSave) {
			this.chunksToSave.put(asLong(x, z), chunk);
		}
	}

	public void savePendingChunk(int x, int z) {
		if (this.cache == null)
			return;

		CompoundTag data;
		synchronized (this.chunksToSave) {
			data = this.chunksToSave.remove(asLong(x, z));
		}
		if (data != null)
			saveToCache(x, z, data);
	}

	public void saveToCache(int x, int z, CompoundTag chunkTag) {
		if (this.cache == null)
			throw new IllegalStateException();

		CompoundTag tag = new CompoundTag();
		tag.putByteArray("Signature", this.signature.bytes());
		tag.put("Level", chunkTag);

		try {
			this.cache.write(x, z, tag);
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to save chunk " + x + " " + z + " to cache in world " + this.world.uniqueId() + ".", e);
		}
	}

	@Nullable
	public CompoundTag readFromCache(int x, int z) {
		if (this.cache == null)
			throw new IllegalStateException();

		try {
			CompoundTag tag = this.cache.read(x, z);
			if (tag != null && new Signature(tag.getByteArray("Signature")).equals(this.signature))
				return tag.getCompound("Level");
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to read chunk " + x + " " + z + " from cache in world " + this.world.uniqueId() + ".", e);
		}

		return null;
	}

	@Override
	public InternalWorld storage() {
		return this.world;
	}

	@Override
	public WorldConfig config() {
		if (this.config == null)
			throw new IllegalStateException("Config not loaded");
		return this.config;
	}

	@Override
	public void setDynamism(int x, int y, int z, int distance) {
		if (this.dynamismEnabled) {
			NetworkChunk chunk = chunk(x >> 4, z >> 4);
			if (chunk != null)
				chunk.setDynamism(x, y, z, distance);
		}
	}

	@Override
	public int dynamism(int x, int y, int z) {
		if (!this.dynamismEnabled)
			return 0;

		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk == null ? 0 : chunk.dynamism(x, y, z);
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk != null && chunk.deobfuscate(x, y, z);
	}

	@Override
	public void deobfuscateArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean silentFail) {
		checkBlockArea(minX, minY, minZ, maxX, maxY, maxZ);

		if (!this.enabled)
			return;

		int minChunkX = minX >> 4, minChunkZ = minZ >> 4, maxChunkX = maxX >> 4, maxChunkZ = maxZ >> 4;
		if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {
			NetworkChunk chunk = chunk(minChunkX, minChunkZ);
			if (chunk == null) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be loaded");
			}

			if (chunk.state() != State.DEOBFUSCATED)
				chunk.deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
			return;
		}

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (chunk(chunkX, chunkZ) == null) {
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
				NetworkChunk chunk = chunk(chunkX, chunkZ);

				if (chunk.state() != State.DEOBFUSCATED) {
					int chunkMinX = chunkX << 4, chunkMinZ = chunkZ << 4;
					chunk.deobfuscate(Math.max(minX, chunkMinX), minY, Math.max(minZ, chunkMinZ),
							Math.min(maxX, chunkMinX + 15), maxY, Math.min(maxZ, chunkMinZ + 15));
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
			NetworkChunk chunk = chunk(minChunkX, minChunkZ);
			if (chunk == null) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be loaded");
			}
			if (chunk.state() != State.OBFUSCATED) {
				if (silentFail)
					return;
				throw new IllegalStateException("Chunk must be obfuscated");
			}

			chunk.reobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
			return;
		}

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				NetworkChunk chunk = chunk(chunkX, chunkZ);
				if (chunk == null) {
					if (silentFail)
						return;
					throw new IllegalStateException("Chunks must be loaded");
				}
				if (chunk.state() != State.OBFUSCATED) {
					if (silentFail)
						return;
					throw new IllegalStateException("Chunks must be obfuscated");
				}
			}
		}

		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
		Vector3i min = new Vector3i(minX, minY, minZ), max = new Vector3i(maxX, maxY, maxZ);

		for (ConfiguredModifier mod : this.config.modifiers) {
			try {
				mod.modifier.modify(this, min, max, this.random, mod.config);
			} catch (Exception ex) {
				Mirage.LOGGER.error("Modifier " + ChunkModifier.REGISTRY_TYPE.get().valueKey(mod.modifier) + " has thrown an exception while (re)modifying a part of a network world", ex);
			}
		}
	}

	@Nullable
	public NetworkChunk chunk(int x, int z) {
		InternalChunk chunk = this.world.opaqueChunk(x, z);
		return chunk != null && chunk.isViewAvailable() ? chunk.view() : null;
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk != null && chunk.isExposed(x, y, z);
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk != null && chunk.isOpaque(x, y, z);
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk != null && chunk.setBlock(x, y, z, block);
	}

	@Override
	public boolean removeBlock(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk != null && chunk.removeBlock(x, y, z);
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
		return this.blockSize;
	}

	@Override
	public boolean contains(int x, int y, int z) {
		return VecHelper.inBounds(x, y, z, this.blockMin, this.blockMax);
	}

	@Override
	public boolean isAreaAvailable(int x, int y, int z) {
		return contains(x, y, z) && isChunkLoaded(x >> 4, z >> 4);
	}

	@Override
	public BlockState block(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk == null ? AIR : chunk.block(x, y, z);
	}

	@Override
	public FluidState fluid(int x, int y, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk == null ? NO_FLUID : chunk.fluid(x, y, z);
	}

	@Override
	public int highestYAt(int x, int z) {
		NetworkChunk chunk = chunk(x >> 4, z >> 4);
		return chunk == null ? 0 : chunk.highestYAt(x, z);
	}

	@Override
	public UUID uniqueId() {
		return this.world.uniqueId();
	}

	@Override
	public VolumeStream<Mutable, BlockState> blockStateStream(Vector3i min, Vector3i max, StreamOptions options) {
		return BlockUtil.blockStateStream(this, min, max, options);
	}
}
