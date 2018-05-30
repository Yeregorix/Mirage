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

package net.smoofyuniverse.mirage.impl.network;

import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.WorldType;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.api.cache.Signature;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.mirage.api.modifier.ConfiguredModifier;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.api.volume.WorldView;
import net.smoofyuniverse.mirage.config.world.WorldConfig;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.cache.ChunkSnapshot;
import net.smoofyuniverse.mirage.impl.network.cache.NetworkRegionCache;
import net.smoofyuniverse.mirage.util.ModifierUtil;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;
import org.spongepowered.api.world.storage.WorldProperties;
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static net.smoofyuniverse.mirage.util.MathUtil.clamp;

/**
 * Represents the world viewed for the network (akka online players)
 */
public class NetworkWorld implements WorldView {
	public static final int CURRENT_CONFIG_VERSION = 1, MINIMUM_CONFIG_VERSION = 1;

	private final Long2ObjectMap<ChunkSnapshot> chunksToSave = new Long2ObjectOpenHashMap<>();
	private final Vector3i blockMin, blockMax, blockSize;
	private final InternalWorld world;

	private List<ConfiguredModifier> modifiers;
	private NetworkRegionCache regionCache;
	private WorldConfig.Immutable config;
	private Signature signature;
	private boolean enabled, dynamismEnabled;

	private Random random = new Random();

	public NetworkWorld(InternalWorld world) {
		this.world = world;
		this.blockMin = world.getBlockMin();
		this.blockMax = world.getBlockMax();
		this.blockSize = world.getBlockSize();
	}

	public void loadConfig() throws IOException, ObjectMappingException {
		if (this.config != null)
			throw new IllegalStateException("Config already loaded");

		Path file = Mirage.get().getWorldConfigsDirectory().resolve(getName() + ".conf");
		ConfigurationLoader<CommentedConfigurationNode> loader = Mirage.get().createConfigLoader(file);

		WorldProperties properties = getProperties();
		DimensionType dimType = properties.getDimensionType();
		WorldType wType = ((net.minecraft.world.World) this.world).getWorldType();

		CommentedConfigurationNode root = loader.load();
		int version = root.getNode("Version").getInt();
		if ((version > CURRENT_CONFIG_VERSION || version < MINIMUM_CONFIG_VERSION) && Mirage.get().backupFile(file)) {
			Mirage.LOGGER.info("Your config version is not supported. A new one will be generated.");
			root = loader.createEmptyNode();
		}

		ConfigurationNode cfgNode = root.getNode("Config");
		WorldConfig cfg = cfgNode.getValue(WorldConfig.TOKEN, new WorldConfig(wType != WorldType.FLAT && wType != WorldType.DEBUG_ALL_BLOCK_STATES && dimType != DimensionTypes.THE_END));

		if (cfg.preobf.blocks == null) {
			cfg.preobf.blocks = new BlockSet();
			ModifierUtil.getCommonResources(cfg.preobf.blocks, dimType);
			ModifierUtil.getRareResources(cfg.preobf.blocks, dimType);
		}
		if (cfg.preobf.replacement == null)
			cfg.preobf.replacement = ModifierUtil.getCommonGround(dimType);

		cfg.deobf.naturalRadius = clamp(cfg.deobf.naturalRadius, 1, 4);
		cfg.deobf.playerRadius = clamp(cfg.deobf.playerRadius, 1, 4);

		if (cfg.seed == 0)
			cfg.seed = ThreadLocalRandom.current().nextLong();

		if (cfg.enabled && wType == WorldType.DEBUG_ALL_BLOCK_STATES) {
			Mirage.LOGGER.warn("Obfuscation is not available in a debug_all_block_states world. Obfuscation will be disabled.");
			cfg.enabled = false;
		}

		ConfigurationNode modsNode = root.getNode("Modifiers");
		if (modsNode.isVirtual()) {
			if (dimType == DimensionTypes.OVERWORLD) {
				modsNode.getAppendedNode().getNode("Id").setValue("bedrock");

				ConfigurationNode water_dungeons = modsNode.getAppendedNode();
				water_dungeons.getNode("Id").setValue("obvious");
				water_dungeons.getNode("Preset").setValue("water_dungeons");
			}

			modsNode.getAppendedNode().getNode("Id").setValue("obvious");
		}

		ImmutableList.Builder<ConfiguredModifier> mods = ImmutableList.builder();
		if (cfg.enabled) {
			for (ConfigurationNode node : modsNode.getChildrenList()) {
				String id = node.getNode("Id").getString();
				if (id == null)
					continue;

				ChunkModifier mod = ChunkModifierRegistryModule.get().getById(id).orElse(null);
				if (mod == null) {
					Mirage.LOGGER.warn("Modifier '" + id + "' does not exists.");
					continue;
				}

				if (!mod.isCompatible(properties)) {
					Mirage.LOGGER.warn("Modifier " + mod.getId() + " is not compatible with this world. This modifier will be ignored.");
					continue;
				}

				Object modCfg;
				try {
					modCfg = mod.loadConfiguration(node.getNode("Options"), properties, node.getNode("Preset").getString("").toLowerCase());
					node.removeChild("Preset");
				} catch (Exception e) {
					Mirage.LOGGER.warn("Modifier " + mod.getId() + " failed to loaded his configuration. This modifier will be ignored.", e);
					continue;
				}

				mods.add(new ConfiguredModifier(mod, modCfg));
			}
		}
		this.modifiers = mods.build();

		if (cfg.enabled && this.modifiers.isEmpty()) {
			Mirage.LOGGER.info("No valid modifier was found. Obfuscation will be disabled.");
			cfg.enabled = false;
		}

		if (cfg.enabled && cfg.cache) {
			boolean useCache = false;
			for (ConfiguredModifier mod : this.modifiers) {
				if (mod.modifier.shouldCache())
					useCache = true;
			}

			if (useCache) {
				Signature.Builder cacheSignature = Signature.builder();
				for (ConfiguredModifier mod : this.modifiers)
					cacheSignature.append(mod.modifier);

				String cacheName = this.world.getUniqueId() + "/" + cacheSignature.build();

				this.regionCache = new NetworkRegionCache(Mirage.get().getCacheDirectory().resolve(cacheName));
				try {
					this.regionCache.loadVersion();
					if (this.regionCache.isVersionSupported()) {
						if (this.regionCache.shouldUpdateVersion()) {
							Mirage.LOGGER.info("Updating cache version in directory " + cacheName + "/ ..");
							this.regionCache.updateVersion();
						}
						this.regionCache.saveVersion();

						Signature.Builder b = Signature.builder().append(cfg.seed).append((byte) (cfg.dynamism ? 1 : 0));
						for (ConfiguredModifier mod : this.modifiers)
							mod.modifier.appendSignature(b, mod.config);
						this.signature = b.build();
					} else {
						Mirage.LOGGER.warn("Cache version in directory " + cacheName + "/ is not supported. Caching will be disabled.");
						this.regionCache = null;
					}
				} catch (Exception e) {
					Mirage.LOGGER.warn("Failed to load cache in directory " + cacheName + "/. Caching will be disabled.", e);
					this.regionCache = null;
				}
			} else {
				Mirage.LOGGER.info("No modifier needing caching was found. Caching will be disabled.");
				cfg.cache = false;
			}
		}

		version = CURRENT_CONFIG_VERSION;
		root.getNode("Version").setValue(version);
		cfgNode.setValue(WorldConfig.TOKEN, cfg);
		loader.save(root);

		this.config = cfg.toImmutable();
		this.enabled = cfg.enabled;
		this.dynamismEnabled = cfg.enabled && cfg.dynamism;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	public boolean useCache() {
		return this.regionCache != null;
	}

	@Nullable
	public Signature getSignature() {
		return this.signature;
	}

	public void addPendingSave(int x, int z, ChunkSnapshot chunk) {
		if (this.regionCache != null)
			this.chunksToSave.put(NetworkChunk.asLong(x, z), chunk);
	}

	public void removePendingSave(int x, int z) {
		this.chunksToSave.remove(NetworkChunk.asLong(x, z));
	}

	public void savePendingChunk(int x, int z) {
		if (this.regionCache != null) {
			ChunkSnapshot chunk = this.chunksToSave.remove(NetworkChunk.asLong(x, z));
			if (chunk != null)
				saveToCache(x, z, chunk);
		}
	}

	public void saveToCache(int x, int z, ChunkSnapshot chunk) {
		if (this.regionCache == null)
			throw new IllegalStateException();

		MirageTimings.WRITING_CACHE.startTimingIfSync();

		try (DataOutputStream out = this.regionCache.getChunkOutputStream(x, z)) {
			this.signature.write(out);
			chunk.write(out);
		} catch (Exception e) {
			Mirage.LOGGER.warn("Failed to save chunk data " + x + " " + z + " to cache in world " + this.world.getName() + ".", e);
		}

		MirageTimings.WRITING_CACHE.stopTimingIfSync();
	}

	@Nullable
	public ChunkSnapshot readFromCache(int x, int z) {
		if (this.regionCache == null)
			throw new IllegalStateException();

		MirageTimings.READING_CACHE.startTimingIfSync();

		try (DataInputStream in = this.regionCache.getChunkInputStream(x, z)) {
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
	public List<ConfiguredModifier> getModifiers() {
		if (this.config == null)
			throw new IllegalStateException("Config not loaded");
		return this.modifiers;
	}

	@Override
	public WorldConfig.Immutable getConfig() {
		if (this.config == null)
			throw new IllegalStateException("Config not loaded");
		return this.config;
	}

	@Override
	public Optional<ChunkView> getChunkView(int x, int y, int z) {
		return Optional.ofNullable(getChunk(x, z));
	}

	@Override
	public Optional<ChunkView> getChunkViewAt(int x, int y, int z) {
		return getChunkView(x >> 4, 0, z >> 4);
	}

	@Override
	public Collection<NetworkChunk> getLoadedChunkViews() {
		if (!this.enabled)
			return ImmutableList.of();

		ImmutableList.Builder<NetworkChunk> b = ImmutableList.builder();
		for (InternalChunk c : this.world.getLoadedChunkStorages()) {
			if (c.isViewAvailable())
				b.add(c.getView());
		}
		return b.build();
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
					chunk.deobfuscate(Math.max(minX, chunkMinX), minY, Math.max(minZ, chunkMinZ), Math.min(maxX, chunkMinX + 15), maxY, Math.min(maxZ, chunkMinZ + 15));
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
				throw new IllegalStateException("Chunk must be fully obfuscated");
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
					throw new IllegalStateException("Chunks must be fully obfuscated");
				}
			}
		}

		deobfuscate(minX, minY, minZ, maxX, maxY, maxZ);
		Vector3i min = new Vector3i(minX, minY, minZ), max = new Vector3i(maxX, maxY, maxZ);

		MirageTimings.REOBFUSCATION.startTiming();

		for (ConfiguredModifier mod : getModifiers()) {
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

	public boolean isChunkLoaded(int x, int z) {
		return getChunk(x, z) != null;
	}

	@Nullable
	public NetworkChunk getChunk(int x, int z) {
		InternalChunk chunk = this.world.getChunk(x, z);
		return chunk != null && chunk.isViewAvailable() ? chunk.getView() : null;
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isExposed(x, y, z);
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
				return new ArrayMutableBlockBuffer(GlobalPalette.instance, this.blockMin, this.blockMax, ExtentBufferUtil.copyToArray(this, this.blockMin, this.blockMax, this.blockSize));
			case THREAD_SAFE:
			default:
				throw new UnsupportedOperationException(type.name());
		}
	}

	@Override
	public ImmutableBlockVolume getImmutableBlockCopy() {
		return ArrayImmutableBlockBuffer.newWithoutArrayClone(GlobalPalette.instance, this.blockMin, this.blockMax, ExtentBufferUtil.copyToArray(this, this.blockMin, this.blockMax, this.blockSize));
	}

	@Override
	public UUID getUniqueId() {
		return this.world.getUniqueId();
	}
}
