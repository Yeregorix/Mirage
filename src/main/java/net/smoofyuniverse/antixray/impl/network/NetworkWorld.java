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

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.WorldType;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.AntiXrayTimings;
import net.smoofyuniverse.antixray.api.cache.Signature;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifier;
import net.smoofyuniverse.antixray.api.modifier.ChunkModifierRegistryModule;
import net.smoofyuniverse.antixray.api.util.ModifierUtil;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import net.smoofyuniverse.antixray.api.volume.WorldView;
import net.smoofyuniverse.antixray.config.WorldConfig;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.cache.ChunkSnapshot;
import net.smoofyuniverse.antixray.impl.network.cache.NetworkRegionCache;
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

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents the world viewed for the network (akka online players)
 */
public class NetworkWorld implements WorldView {
	public static final int CURRENT_CONFIG_VERSION = 1, MINIMUM_CONFIG_VERSION = 1;

	private Long2ObjectMap<ChunkSnapshot> chunksToSave = new Long2ObjectOpenHashMap<>();
	private Map<ChunkModifier, Object> modifiers;
	private NetworkRegionCache regionCache;
	private WorldConfig.Immutable config;
	private Signature signature;
	private InternalWorld world;
	private boolean enabled;

	public NetworkWorld(InternalWorld w) {
		this.world = w;
	}

	public void loadConfig() throws IOException, ObjectMappingException {
		if (this.config != null)
			throw new IllegalStateException("Config already loaded");

		String name = getName();
		AntiXray.LOGGER.info("Loading configuration for world " + name + " ..");

		Path file = AntiXray.get().getWorldConfigsDirectory().resolve(name + ".conf");
		ConfigurationLoader<CommentedConfigurationNode> loader = AntiXray.get().createConfigLoader(file);

		WorldProperties properties = getProperties();
		DimensionType dimType = properties.getDimensionType();
		WorldType wType = ((net.minecraft.world.World) this.world).getWorldType();

		CommentedConfigurationNode root = loader.load();
		int version = root.getNode("Version").getInt();
		if ((version > CURRENT_CONFIG_VERSION || version < MINIMUM_CONFIG_VERSION) && AntiXray.get().backupFile(file)) {
			AntiXray.LOGGER.info("Your config version is not supported. A new one will be generated.");
			root = loader.createEmptyNode();
		}

		ConfigurationNode cfgNode = root.getNode("Config");
		WorldConfig cfg = cfgNode.getValue(WorldConfig.TOKEN, new WorldConfig(wType != WorldType.FLAT && wType != WorldType.DEBUG_ALL_BLOCK_STATES && dimType != DimensionTypes.THE_END));

		if (cfg.preobf.blocks == null)
			cfg.preobf.blocks = ModifierUtil.getCommonOres(dimType);
		if (cfg.preobf.replacement == null)
			cfg.preobf.replacement = ModifierUtil.getCommonGround(dimType);

		if (cfg.deobf.naturalRadius < 1)
			cfg.deobf.naturalRadius = 1;
		if (cfg.deobf.naturalRadius > 4)
			cfg.deobf.naturalRadius = 4;

		if (cfg.deobf.playerRadius < 1)
			cfg.deobf.playerRadius = 1;
		if (cfg.deobf.playerRadius > 4)
			cfg.deobf.playerRadius = 4;

		if (cfg.seed == 0)
			cfg.seed = ThreadLocalRandom.current().nextLong();

		if (cfg.enabled && wType == WorldType.DEBUG_ALL_BLOCK_STATES) {
			AntiXray.LOGGER.warn("Obfuscation is not available in a debug_all_block_states world. Obfuscation will be disabled.");
			cfg.enabled = false;
		}

		ConfigurationNode modsNode = root.getNode("Modifiers");
		if (modsNode.isVirtual()) {
			if (dimType == DimensionTypes.OVERWORLD)
				modsNode.getAppendedNode().getNode("Id").setValue("bedrock");
			modsNode.getAppendedNode().getNode("Id").setValue("obvious");
		}

		ImmutableMap.Builder<ChunkModifier, Object> mods = ImmutableMap.builder();
		if (cfg.enabled) {
			for (ConfigurationNode node : modsNode.getChildrenList()) {
				String id = node.getNode("Id").getString();
				if (id == null)
					continue;

				ChunkModifier mod = ChunkModifierRegistryModule.get().getById(id).orElse(null);
				if (mod == null) {
					AntiXray.LOGGER.warn("Modifier '" + id + "' does not exists.");
					continue;
				}

				if (!mod.isCompatible(properties)) {
					AntiXray.LOGGER.warn("Modifier " + mod.getId() + " is not compatible with this world. This modifier will be ignored.");
					continue;
				}

				Object modCfg;
				try {
					modCfg = mod.loadConfiguration(node.getNode("Options"), properties);
				} catch (Exception e) {
					AntiXray.LOGGER.warn("Modifier " + mod.getId() + " failed to loaded his configuration. This modifier will be ignored.", e);
					continue;
				}

				mods.put(mod, modCfg);
			}
		}
		this.modifiers = mods.build();

		if (cfg.enabled && this.modifiers.isEmpty()) {
			AntiXray.LOGGER.info("No valid modifier was found. Obfuscation will be disabled.");
			cfg.enabled = false;
		}

		if (cfg.enabled && cfg.cache) {
			boolean useCache = false;
			for (ChunkModifier mod : this.modifiers.keySet()) {
				if (mod.shouldCache())
					useCache = true;
			}

			if (useCache) {
				String cacheName = this.world.getUniqueId() + "/" + Signature.builder().append(this.modifiers.keySet()).build();

				this.regionCache = new NetworkRegionCache(AntiXray.get().getCacheDirectory().resolve(cacheName));
				try {
					if (this.regionCache.isVersionSupported()) {
						this.regionCache.updateVersion();
						this.regionCache.saveVersion();

						Signature.Builder b = Signature.builder().append(cfg.seed);
						for (Entry<ChunkModifier, Object> e : this.modifiers.entrySet())
							e.getKey().appendSignature(b, e.getValue());
						this.signature = b.build();
					} else {
						AntiXray.LOGGER.warn("Cache version in directory " + cacheName + "/ is not supported. Caching will be disabled.");
						this.regionCache = null;
					}
				} catch (Exception e) {
					AntiXray.LOGGER.warn("Failed to load cache in directory " + cacheName + "/. Caching will be disabled.", e);
					this.regionCache = null;
				}
			} else {
				AntiXray.LOGGER.info("No modifier needing caching was found. Caching will be disabled.");
				cfg.cache = false;
			}
		}

		version = CURRENT_CONFIG_VERSION;
		root.getNode("Version").setValue(version);
		cfgNode.setValue(WorldConfig.TOKEN, cfg);
		loader.save(root);

		this.config = cfg.toImmutable();
		this.enabled = cfg.enabled;
	}

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

		AntiXrayTimings.WRITING_CACHE.startTimingIfSync();

		try (DataOutputStream out = this.regionCache.getChunkOutputStream(x, z)) {
			this.signature.write(out);
			chunk.write(out);
		} catch (Exception e) {
			AntiXray.LOGGER.warn("Failed to save chunk data " + x + " " + z + " to cache in world " + this.world.getName() + ".", e);
		}

		AntiXrayTimings.WRITING_CACHE.stopTimingIfSync();
	}

	@Nullable
	public ChunkSnapshot readFromCache(int x, int z) {
		if (this.regionCache == null)
			throw new IllegalStateException();

		AntiXrayTimings.READING_CACHE.startTimingIfSync();

		try (DataInputStream in = this.regionCache.getChunkInputStream(x, z)) {
			if (in != null && Signature.read(in).equals(this.signature))
				return new ChunkSnapshot().read(in);
		} catch (Exception e) {
			AntiXray.LOGGER.warn("Failed to read chunk data " + x + " " + z + " from cache in world " + this.world.getName() + ".", e);
		} finally {
			AntiXrayTimings.READING_CACHE.stopTimingIfSync();
		}

		return null;
	}

	@Override
	public InternalWorld getStorage() {
		return this.world;
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
	public Map<ChunkModifier, Object> getModifiers() {
		return this.modifiers;
	}

	@Override
	public WorldConfig.Immutable getConfig() {
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

	@Nullable
	public NetworkChunk getChunk(int x, int z) {
		InternalChunk chunk = this.world.getChunk(x, z);
		return chunk == null ? null : chunk.getView();
	}

	public boolean isChunkLoaded(int x, int z) {
		return getChunk(x, z) != null;
	}

	@Override
	public boolean deobfuscate(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.deobfuscate(x & 15, y, z & 15);
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isExposed(x & 15, y, z & 15);
	}

	@Override
	public boolean setBlock(int x, int y, int z, BlockState block) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk != null && chunk.setBlock(x & 15, y, z & 15, block);
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

	@Override
	public Vector3i getBlockMin() {
		return this.world.getBlockMin();
	}

	@Override
	public Vector3i getBlockMax() {
		return this.world.getBlockMax();
	}

	@Override
	public Vector3i getBlockSize() {
		return this.world.getBlockSize();
	}

	@Override
	public boolean containsBlock(int x, int y, int z) {
		return this.world.containsBlock(x, y, z);
	}

	@Override
	public BlockState getBlock(int x, int y, int z) {
		NetworkChunk chunk = getChunk(x >> 4, z >> 4);
		return chunk == null ? BlockTypes.AIR.getDefaultState() : chunk.getBlock(x & 15, y, z & 15);
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

	@Override
	public UUID getUniqueId() {
		return this.world.getUniqueId();
	}
}
