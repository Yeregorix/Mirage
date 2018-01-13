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
import net.minecraft.world.WorldType;
import net.smoofyuniverse.antixray.AntiXray;
import net.smoofyuniverse.antixray.api.ModifierRegistry;
import net.smoofyuniverse.antixray.api.ViewModifier;
import net.smoofyuniverse.antixray.api.volume.ChunkView;
import net.smoofyuniverse.antixray.api.volume.WorldView;
import net.smoofyuniverse.antixray.config.Options;
import net.smoofyuniverse.antixray.config.WorldConfig;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.modifier.EmptyModifier;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represent the world viewed for the network (akka online players)
 */
public class NetworkWorld implements WorldView {
	private ConfigurationLoader<CommentedConfigurationNode> loader;
	private WorldConfig config;
	private ViewModifier modifier;
	private Options options;
	private InternalWorld world;
	private boolean enabled;

	public NetworkWorld(InternalWorld w) {
		this.world = w;
	}

	public void loadConfig() throws IOException, ObjectMappingException {
		if (this.enabled)
			throw new IllegalStateException("Already loaded");

		if (this.loader == null)
			this.loader = AntiXray.get().createConfigLoader(((World) this.world).getName());

		DimensionType dimType = ((World) this.world).getDimension().getType();
		WorldType wType = ((net.minecraft.world.World) this.world).getWorldType();

		CommentedConfigurationNode node = this.loader.load();
		this.config = node.getValue(WorldConfig.TOKEN, new WorldConfig(wType != WorldType.FLAT && wType != WorldType.DEBUG_ALL_BLOCK_STATES && dimType != DimensionTypes.THE_END));

		if (this.config.ground == null) {
			if (dimType == DimensionTypes.NETHER)
				this.config.ground = BlockTypes.NETHERRACK.getDefaultState();
			else if (dimType == DimensionTypes.THE_END)
				this.config.ground = BlockTypes.END_STONE.getDefaultState();
			else
				this.config.ground = BlockTypes.STONE.getDefaultState();
		}

		if (this.config.ores == null) {
			List<BlockState> ores = this.config.ores = new ArrayList<>();
			if (dimType == DimensionTypes.NETHER) {
				ores.add(BlockTypes.QUARTZ_ORE.getDefaultState());
			} else {
				ores.add(BlockTypes.REDSTONE_ORE.getDefaultState());
				ores.add(BlockTypes.EMERALD_ORE.getDefaultState());
				ores.add(BlockTypes.DIAMOND_ORE.getDefaultState());
				ores.add(BlockTypes.COAL_ORE.getDefaultState());
				ores.add(BlockTypes.IRON_ORE.getDefaultState());
				ores.add(BlockTypes.LAPIS_ORE.getDefaultState());
				ores.add(BlockTypes.GOLD_ORE.getDefaultState());
			}
		}

		if (this.config.deobfRadius < 1)
			this.config.deobfRadius = 1;
		if (this.config.deobfRadius > 4)
			this.config.deobfRadius = 4;

		if (this.config.density < 0)
			this.config.density = 0;
		if (this.config.density > 1)
			this.config.density = 1;

		if (this.config.seed == 0)
			this.config.seed = ThreadLocalRandom.current().nextLong();

		this.config.modifier = this.config.modifier.toLowerCase();
		Optional<ViewModifier> mod = ModifierRegistry.get(this.config.modifier);
		if (mod.isPresent())
			this.modifier = mod.get();
		else {
			if (this.config.enabled)
				AntiXray.LOGGER.warn("Modifier '" + this.config.modifier + "' does not exists. Obfuscation in world " + ((World) this.world).getName() + " will be disabled.");
			this.modifier = EmptyModifier.INSTANCE;
			this.config.modifier = "empty";
			this.config.enabled = false;
		}

		if (this.config.enabled) {
			if (wType == WorldType.DEBUG_ALL_BLOCK_STATES) {
				AntiXray.LOGGER.warn("Obfuscation is not available in a debug_all_block_states world. Obfuscation in world " + ((World) this.world).getName() + " will be disabled.");
				this.config.enabled = false;
			}
		}

		node.setValue(WorldConfig.TOKEN, this.config);
		this.loader.save(node);

		this.options = this.config.asOptions();
		this.enabled = this.config.enabled;
	}

	public WorldConfig getConfig() {
		return this.config;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public InternalWorld getStorage() {
		return this.world;
	}

	@Override
	public ViewModifier getModifier() {
		return this.modifier;
	}

	@Override
	public Options getOptions() {
		return this.options;
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
}
