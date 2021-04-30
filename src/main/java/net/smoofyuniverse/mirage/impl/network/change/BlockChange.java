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

package net.smoofyuniverse.mirage.impl.network.change;

import com.flowpowered.math.vector.Vector3i;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.compat.CompatUtil;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.living.player.Player;

import java.util.ArrayList;
import java.util.List;

public class BlockChange {
	private final Packet<?>[] packets;

	private BlockChange(Packet<?>... packets) {
		this.packets = packets;
	}

	private BlockChange() {
		this.packets = null;
	}

	public void sendTo(Player player) {
		if (this.packets != null) {
			for (Packet<?> packet : this.packets)
				((EntityPlayerMP) player).connection.sendPacket(packet);
		}
	}

	public void sendTo(Iterable<Player> players) {
		if (this.packets != null) {
			for (Player p : players) {
				for (Packet<?> packet : this.packets)
					((EntityPlayerMP) p).connection.sendPacket(packet);
			}
		}
	}

	public static Builder builder(InternalChunk chunk) {
		return new Builder((Chunk) chunk);
	}

	public static class Builder {
		private final Short2ObjectMap<IBlockState> blocks = new Short2ObjectOpenHashMap<>();
		private final World nmsWorld;
		private final int x, z;

		private Builder(Chunk nmsChunk) {
			this.nmsWorld = nmsChunk.getWorld();
			this.x = nmsChunk.x;
			this.z = nmsChunk.z;
		}

		public void add(Vector3i pos, BlockState state) {
			add(pos.getX(), pos.getY(), pos.getZ(), state);
		}

		public void add(int x, int y, int z, BlockState state) {
			add((short) ((x & 15) << 12 | (z & 15) << 8 | (y & 255)), state);
		}

		public void add(short index, BlockState state) {
			this.blocks.put(index, (IBlockState) state);
		}

		public BlockChange build(boolean tileEntities) {
			int changes = this.blocks.size();

			if (changes == 0)
				return new BlockChange();

			int minX = this.x << 4, minZ = this.z << 4;

			if (changes == 1) {
				SPacketBlockChange packet = new SPacketBlockChange();

				Entry<IBlockState> e = this.blocks.short2ObjectEntrySet().iterator().next();
				short pos = e.getShortKey();

				packet.blockPosition = new BlockPos(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15));
				packet.blockState = e.getValue();

				if (tileEntities && CompatUtil.hasTileEntity(packet.blockState)) {
					TileEntity te = this.nmsWorld.getTileEntity(packet.blockPosition);
					if (te != null) {
						SPacketUpdateTileEntity packet2 = te.getUpdatePacket();
						if (packet2 != null)
							return new BlockChange(packet, packet2);
					}
				}

				return new BlockChange(packet);
			} else {
				SPacketMultiBlockChange packet = new SPacketMultiBlockChange();

				packet.chunkPos = new ChunkPos(this.x, this.z);
				packet.changedBlocks = new BlockUpdateData[changes];

				int i = 0;
				for (Entry<IBlockState> e : this.blocks.short2ObjectEntrySet())
					packet.changedBlocks[i++] = packet.new BlockUpdateData(e.getShortKey(), e.getValue());

				if (tileEntities) {
					List<Packet<?>> packets = new ArrayList<>();
					packets.add(packet);

					for (BlockUpdateData b : packet.changedBlocks) {
						if (CompatUtil.hasTileEntity(b.blockState)) {
							short pos = b.offset;

							TileEntity te = this.nmsWorld.getTileEntity(new BlockPos(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
							if (te != null) {
								SPacketUpdateTileEntity packet2 = te.getUpdatePacket();
								if (packet2 != null)
									packets.add(packet2);
							}
						}
					}

					return new BlockChange(packets.toArray(new Packet<?>[0]));
				}

				return new BlockChange(packet);
			}
		}
	}
}
