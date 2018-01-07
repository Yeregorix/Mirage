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

package net.smoofyuniverse.noxray.api;

import net.smoofyuniverse.noxray.NoXray;
import net.smoofyuniverse.noxray.config.Options;
import net.smoofyuniverse.noxray.config.WorldConfig;
import net.smoofyuniverse.noxray.modifier.ModifierRegistry;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represent the world viewed for the network (akka online players)
 */
public class NetworkWorld {
	private ConfigurationLoader<CommentedConfigurationNode> loader;
	private WorldConfig config;
	private BlockModifier modifier;
	private Options options;
	private final World world;

	public NetworkWorld(World w) {
		this.world = w;
	}

	public void loadConfig() throws IOException, ObjectMappingException {
		if (this.loader == null)
			this.loader = NoXray.get().createConfigLoader(this.world.getName());

		CommentedConfigurationNode node = this.loader.load();
		this.config = node.getValue(WorldConfig.TOKEN, new WorldConfig());

		DimensionType dimType = this.world.getDimension().getType();

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
		if (this.config.deobfRadius > 8)
			this.config.deobfRadius = 8;

		if (this.config.density < 0)
			this.config.density = 0;
		if (this.config.density > 1)
			this.config.density = 1;

		node.setValue(WorldConfig.TOKEN, this.config);
		this.loader.save(node);

		BlockModifier mod = ModifierRegistry.get(this.config.modifier);
		if (mod == null) {
			NoXray.LOGGER.warn("Modifier '" + this.config.modifier.toLowerCase() + "' does not exists.");
			this.modifier = BlockModifier.EMPTY;
		} else
			this.modifier = mod;

		this.options = this.config.asOptions();
	}

	public WorldConfig getConfig() {
		return this.config;
	}

	public Options getOptions() {
		return this.options;
	}

	public BlockModifier getModifier() {
		return this.modifier;
	}

	public World getWorld() {
		return this.world;
	}
}
