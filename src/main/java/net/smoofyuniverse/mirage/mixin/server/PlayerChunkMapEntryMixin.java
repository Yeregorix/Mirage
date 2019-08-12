/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.mixin.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.mirage.MirageTimings;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalChunkMap;
import net.smoofyuniverse.mirage.impl.internal.compat.CompatUtil;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.change.BlockChange;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamism.DynamicChunk;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.extent.BlockVolume;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = PlayerChunkMapEntry.class, priority = 900)
public abstract class PlayerChunkMapEntryMixin implements ChunkChangeListener {
	@Shadow
	private Chunk chunk;
	@Shadow
	private int changes;
	@Shadow
	private int changedSectionFilter;
	@Shadow
	private boolean sentToPlayers;
	@Shadow
	@Final
	private List<EntityPlayerMP> players;
	@Shadow
	private short[] changedBlocks;
	@Shadow
	@Final
	private ChunkPos pos;
	@Shadow
	@Final
	private PlayerChunkMap playerChunkMap;

	private Map<EntityPlayerMP, DynamicChunk> dynamicChunks;
	private boolean dynamismEnabled, dirty;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		this.dynamismEnabled = ((InternalChunkMap) this.playerChunkMap).isDynamismEnabled();
		if (this.dynamismEnabled)
			this.dynamicChunks = new HashMap<>();
	}

	@Inject(method = "addPlayer", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = Shift.AFTER))
	public void onAddPlayer(EntityPlayerMP player, CallbackInfo ci) {
		if (this.sentToPlayers && this.dynamismEnabled)
			addDynamicChunk(player);
	}

	private void addDynamicChunk(EntityPlayerMP player) {
		DynamicChunk chunk = new DynamicChunk(this.pos.x, this.pos.z);
		this.dynamicChunks.put(player, chunk);
		((InternalChunkMap) this.playerChunkMap).getOrCreateDynamismManager((Player) player).addChunk(chunk, this);
	}

	@Inject(method = "removePlayer", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z", shift = Shift.AFTER))
	public void onRemovePlayer(EntityPlayerMP player, CallbackInfo ci) {
		if (this.sentToPlayers && this.dynamismEnabled)
			removeDynamicChunk(player);
	}

	private void removeDynamicChunk(EntityPlayerMP player) {
		this.dynamicChunks.remove(player);
		((InternalChunkMap) this.playerChunkMap).getDynamismManager(player.getUniqueID()).ifPresent(m -> m.removeChunk(this.pos.x, this.pos.z));
	}

	/**
	 * @author Yeregorix
	 * @reason Implements dynamism
	 */
	@Overwrite
	public boolean sendToPlayers() {
		if (this.sentToPlayers)
			return true;

		if (this.chunk == null || !this.chunk.isPopulated())
			return false;

		InternalChunk chunk = (InternalChunk) this.chunk;
		if (chunk.isViewAvailable())
			chunk.getView().obfuscate();

		clearChanges();
		this.sentToPlayers = true;

		if (chunk.isViewAvailable())
			chunk.getView().setListener(this);

		if (this.players.isEmpty())
			return true;

		if (this.dynamismEnabled) {
			for (EntityPlayerMP p : this.players)
				addDynamicChunk(p);

			Packet<?> packet = new SPacketChunkData(this.chunk, 65535);

			for (Map.Entry<EntityPlayerMP, DynamicChunk> e : this.dynamicChunks.entrySet()) {
				EntityPlayerMP p = e.getKey();
				DynamicChunk dynChunk = e.getValue();

				p.connection.sendPacket(packet);

				MirageTimings.DYNAMISM.startTiming();

				dynChunk.applyChanges();
				dynChunk.getCurrent(chunk, true).sendTo((Player) p);

				MirageTimings.DYNAMISM.stopTiming();

				this.playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(p, this.chunk);
				CompatUtil.postChunkWatchEvent(this.chunk, p);
			}
		} else {
			Packet<?> packet = new SPacketChunkData(this.chunk, 65535);

			for (EntityPlayerMP p : this.players) {
				p.connection.sendPacket(packet);

				this.playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(p, this.chunk);
				CompatUtil.postChunkWatchEvent(this.chunk, p);
			}
		}

		return true;
	}

	/**
	 * @author Yeregorix
	 * @reason Implements dynamism
	 */
	@Overwrite
	public void sendToPlayer(EntityPlayerMP p) {
		if (this.sentToPlayers) {
			p.connection.sendPacket(new SPacketChunkData(this.chunk, 65535));

			if (this.dynamismEnabled) {
				MirageTimings.DYNAMISM.startTiming();

				DynamicChunk dynChunk = this.dynamicChunks.get(p);
				if (dynChunk != null) {
					dynChunk.applyChanges();
					dynChunk.getCurrent((InternalChunk) this.chunk, true).sendTo((Player) p);
				}

				MirageTimings.DYNAMISM.stopTiming();
			}

			this.playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(p, this.chunk);
		}
	}

	/**
	 * @author Yeregorix
	 * @reason Implements dynamism
	 */
	@Overwrite
	public void blockChanged(int x, int y, int z) {
		if (this.sentToPlayers) {
			this.changedSectionFilter |= 1 << (y >> 4);

			if (this.changes < 64) {
				short pos = (short) (x << 12 | z << 8 | y);

				for (int i = 0; i < this.changes; i++) {
					if (this.changedBlocks[i] == pos)
						return;
				}

				this.changedBlocks[this.changes++] = pos;
			}

			markDirty();
		}
	}

	/**
	 * @author Yeregorix
	 * @reason Implements dynamism
	 */
	@Overwrite
	public void update() {
		if (this.sentToPlayers) {
			this.dirty = false;

			if (this.changes == 0) {
				if (this.dynamismEnabled) {
					InternalChunk chunk = (InternalChunk) this.chunk;
					MirageTimings.DYNAMISM.startTiming();

					for (Map.Entry<EntityPlayerMP, DynamicChunk> e : this.dynamicChunks.entrySet()) {
						DynamicChunk dynChunk = e.getValue();

						if (dynChunk.hasChanges()) {
							BlockChange.Builder b = BlockChange.builder(chunk);
							dynChunk.getChanges(chunk, b);
							dynChunk.applyChanges();
							b.build(true).sendTo((Player) e.getKey());
						}
					}

					MirageTimings.DYNAMISM.stopTiming();
				}
			} else {
				if (this.changes >= 64) {
					sendPacket(new SPacketChunkData(this.chunk, this.changedSectionFilter));
				} else {
					InternalChunk chunk = (InternalChunk) this.chunk;
					int minX = this.pos.x << 4, minZ = this.pos.z << 4;

					if (this.dynamismEnabled) {
						NetworkChunk netChunk = chunk.getView();
						MirageTimings.DYNAMISM.startTiming();

						for (Map.Entry<EntityPlayerMP, DynamicChunk> e : this.dynamicChunks.entrySet()) {
							EntityPlayerMP p = e.getKey();
							DynamicChunk dynChunk = e.getValue();

							BlockChange.Builder b = BlockChange.builder(chunk);
							for (int i = 0; i < this.changes; i++) {
								short pos = this.changedBlocks[i];
								b.add(pos, (dynChunk.currentlyContains(pos) ? chunk : netChunk).getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
							}

							dynChunk.getChanges(chunk, b);
							dynChunk.applyChanges();

							b.build(true).sendTo((Player) p);
						}

						MirageTimings.DYNAMISM.stopTiming();
					} else {
						BlockVolume volume = chunk.isViewAvailable() ? chunk.getView() : chunk;

						BlockChange.Builder b = BlockChange.builder(chunk);
						for (int i = 0; i < this.changes; i++) {
							short pos = this.changedBlocks[i];
							b.add(pos, volume.getBlock(minX + (pos >> 12 & 15), pos & 255, minZ + (pos >> 8 & 15)));
						}

						b.build(true).sendTo((Iterable) this.players);
					}
				}

				clearChanges();
			}
		}
	}

	/**
	 * @author Yeregorix
	 * @reason Implements dynamism
	 */
	@Overwrite
	public void sendPacket(Packet<?> packet) {
		if (this.sentToPlayers) {
			if (this.dynamismEnabled && packet instanceof SPacketChunkData) {
				SPacketChunkData data = (SPacketChunkData) packet;
				if (data.chunkX != this.pos.x || data.chunkZ != this.pos.z)
					throw new IllegalArgumentException("Chunk pos");

				InternalChunk chunk = (InternalChunk) this.chunk;
				int sections = data.isFullChunk() ? 65535 : data.availableSections;

				for (Map.Entry<EntityPlayerMP, DynamicChunk> e : this.dynamicChunks.entrySet()) {
					EntityPlayerMP p = e.getKey();
					DynamicChunk dynChunk = e.getValue();

					p.connection.sendPacket(packet);

					MirageTimings.DYNAMISM.startTiming();

					dynChunk.applyChanges();
					dynChunk.getCurrent(chunk, sections, true).sendTo((Player) p);

					MirageTimings.DYNAMISM.stopTiming();
				}
			} else {
				for (EntityPlayerMP p : this.players)
					p.connection.sendPacket(packet);
			}
		}
	}

	@Nullable
	@Override
	public InternalChunk getChunk() {
		return (InternalChunk) this.chunk;
	}

	@Override
	public void addChange(int x, int y, int z) {
		blockChanged(x, y, z);
	}

	@Override
	public void sendChanges() {
		update();
	}

	@Override
	public void clearChanges() {
		this.changes = 0;
		this.changedSectionFilter = 0;
	}

	@Override
	public void updateDynamism(int x, int y, int z, int distance) {
		if (this.sentToPlayers && this.dynamismEnabled) {
			for (DynamicChunk chunk : this.dynamicChunks.values())
				chunk.update(x, y, z, distance);
			markDirty();
		}
	}

	@Override
	public void clearDynamism() {
		if (this.sentToPlayers && this.dynamismEnabled) {
			this.dynamicChunks.values().forEach(DynamicChunk::clear);
			markDirty();
		}
	}

	@Override
	public void markDirty() {
		if (this.sentToPlayers && !this.dirty) {
			this.playerChunkMap.entryChanged((PlayerChunkMapEntry) (Object) this);
			this.dirty = true;
		}
	}
}
