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

package net.smoofyuniverse.mirage.mixin.level;

import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkHolder.PlayerProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.change.BlockChanges;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicChunk;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;
import org.spongepowered.api.world.volume.block.BlockVolume;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.stream.Stream;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements ChunkChangeListener {
	@Shadow
	@Final
	ChunkPos pos;
	@Shadow
	private boolean hasChangedSections;
	@Shadow
	@Final
	private ShortSet[] changedBlocksPerSection;
	@Shadow
	@Final
	private PlayerProvider playerProvider;
	@Shadow
	@Final
	private BitSet skyChangedLightSectionFilter;
	@Shadow
	@Final
	private BitSet blockChangedLightSectionFilter;
	@Shadow
	private boolean resendLight;
	@Shadow
	@Final
	private LevelLightEngine lightEngine;
	@Shadow
	@Final
	private LevelHeightAccessor levelHeightAccessor;

	private boolean dynamismEnabled;

	/**
	 * @author Yeregorix
	 * @reason Mirage implementation
	 */
	@Overwrite
	public void broadcastChanges(LevelChunk chunk) {
		if (this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
			int totalChanges = 0;
			for (ShortSet changes : this.changedBlocksPerSection) {
				totalChanges += changes != null ? changes.size() : 0;
			}

			this.resendLight |= totalChanges >= 64;
			if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
				broadcast(new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter, true), !this.resendLight);
				this.skyChangedLightSectionFilter.clear();
				this.blockChangedLightSectionFilter.clear();
			}

			InternalChunk storage = (InternalChunk) chunk;
			int minX = this.pos.x << 4, minZ = this.pos.z << 4;

			if (this.dynamismEnabled) {
				NetworkChunk view = storage.view();

				for (ServerPlayer p : getPlayers()) {
					DynamicChunk dynChunk = ((InternalPlayer) p).getDynamicChunk(this.pos.x, this.pos.z);

					for (int i = 0; i < this.changedBlocksPerSection.length; i++) {
						int y = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
						DynamicSection dynSection = dynChunk == null ? null : dynChunk.sections[i];
						BlockChanges changes = new BlockChanges(chunk, y);
						int minY = y << 4;

						ShortSet storageChanges = this.changedBlocksPerSection[i];
						if (storageChanges != null) {
							ShortIterator it = storageChanges.iterator();
							while (it.hasNext()) {
								short pos = it.nextShort();
								changes.add(pos, (BlockState) (dynSection != null && dynSection.currentlyContains(pos) ? storage : view)
										.block(minX + (pos >> 8 & 15), minY + (pos & 15), minZ + (pos >> 4 & 15)));
							}
						}

						if (dynSection != null) {
							dynSection.getChanges(changes);
							dynSection.applyChanges();
						}

						changes.sendTo(p);
					}
				}
			} else {
				BlockVolume volume = storage.isViewAvailable() ? storage.view() : storage;
				List<Packet<?>> packets = new ArrayList<>();

				for (int i = 0; i < this.changedBlocksPerSection.length; i++) {
					ShortSet storageChanges = this.changedBlocksPerSection[i];
					if (storageChanges != null) {
						int y = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
						BlockChanges changes = new BlockChanges(chunk, y);
						int minY = y << 4;

						ShortIterator it = storageChanges.iterator();
						while (it.hasNext()) {
							short pos = it.nextShort();
							changes.add(pos, (BlockState) volume.block(minX + (pos >> 8 & 15), minY + (pos & 15), minZ + (pos >> 4 & 15)));
						}

						changes.sendTo(packets::add);
					}
				}

				for (ServerPlayer p : getPlayers()) {
					for (Packet<?> packet : packets)
						p.connection.send(packet);
				}
			}

			clearChanges();
		}
	}

	@Shadow
	protected abstract void broadcast(Packet<?> param0, boolean param1);

	@Shadow
	public abstract void blockChanged(BlockPos pos);

	private List<ServerPlayer> getPlayers() {
		return this.playerProvider.getPlayers(this.pos, false);
	}

	public void clearChanges() {
		if (this.hasChangedSections) {
			Arrays.fill(this.changedBlocksPerSection, null);
			this.hasChangedSections = false;
		}
	}

	@Override
	public void addChange(int x, int y, int z) {
		blockChanged(new BlockPos(x, y, z));
	}

	@Override
	public void setDynamismEnabled(boolean value) {
		this.dynamismEnabled = value;
	}

	@Override
	public void updateDynamism(int x, int y, int z, int distance) {
		if (this.dynamismEnabled) {
			getDynamicChunks().forEach(c -> c.update(x, y, z, distance));
			markChanged();
		}
	}

	private Stream<DynamicChunk> getDynamicChunks() {
		return getPlayers().stream().map(p -> ((InternalPlayer) p).getDynamicChunk(this.pos.x, this.pos.z)).filter(Objects::nonNull);
	}

	@Override
	public void clearDynamism() {
		if (this.dynamismEnabled) {
			getDynamicChunks().forEach(DynamicChunk::clear);
			markChanged();
		}
	}

	@Override
	public void markChanged() {
		this.hasChangedSections = true;
	}
}
