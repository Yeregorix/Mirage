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

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.noxray.NoXray;
import net.smoofyuniverse.noxray.api.ModifierRegistry;
import net.smoofyuniverse.noxray.api.ViewModifier;
import net.smoofyuniverse.noxray.api.volume.ChunkView;
import net.smoofyuniverse.noxray.api.volume.WorldView;
import net.smoofyuniverse.noxray.config.Options;
import net.smoofyuniverse.noxray.config.WorldConfig;
import net.smoofyuniverse.noxray.impl.internal.InternalChunk;
import net.smoofyuniverse.noxray.impl.internal.InternalWorld;
import net.smoofyuniverse.noxray.modifier.EmptyModifier;
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

/**
 * Represent the world viewed for the network (akka online players)
 */
public class NetworkWorld implements WorldView {
	private ConfigurationLoader<CommentedConfigurationNode> loader;
	private WorldConfig config;
	private ViewModifier modifier;
	private Options options;
	private InternalWorld world;

	public NetworkWorld(InternalWorld w) {
		this.world = w;
	}

	public void loadConfig() throws IOException, ObjectMappingException {
		if (this.loader == null)
			this.loader = NoXray.get().createConfigLoader(((World) this.world).getName());

		CommentedConfigurationNode node = this.loader.load();
		this.config = node.getValue(WorldConfig.TOKEN, new WorldConfig());

		DimensionType dimType = ((World) this.world).getDimension().getType();

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

		this.config.modifier = this.config.modifier.toLowerCase();
		ViewModifier mod = ModifierRegistry.get(this.config.modifier).orElse(null);
		if (mod == null) {
			NoXray.LOGGER.warn("Modifier '" + this.config.modifier + "' does not exists.");
			this.modifier = EmptyModifier.INSTANCE;
			this.config.modifier = "empty";
		} else
			this.modifier = mod;

		node.setValue(WorldConfig.TOKEN, this.config);
		this.loader.save(node);

		this.options = this.config.asOptions();
	}

	public WorldConfig getConfig() {
		return this.config;
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
