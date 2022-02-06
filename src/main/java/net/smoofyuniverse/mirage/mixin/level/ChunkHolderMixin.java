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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.change.BlockChange;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicChunk;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.volume.block.BlockVolume;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements ChunkChangeListener {
	@Shadow
	@Final
	private ChunkPos pos;
	@Shadow
	private boolean hasChangedSections;
	@Shadow
	@Final
	private ShortSet[] changedBlocksPerSection;
	@Shadow
	@Final
	private PlayerProvider playerProvider;
	@Shadow
	private int skyChangedLightSectionFilter;
	@Shadow
	private int blockChangedLightSectionFilter;
	@Shadow
	private boolean resendLight;
	@Shadow
	@Final
	private LevelLightEngine lightEngine;
	private boolean dynamismEnabled;

	/**
	 * @author Yeregorix
	 * @reason Mirage implementation
	 */
	@Overwrite
	public void broadcastChanges(LevelChunk chunk) {
		if (this.hasChangedSections || this.skyChangedLightSectionFilter != 0 || this.blockChangedLightSectionFilter != 0) {
			int totalChanges = 0;
			for (ShortSet changes : this.changedBlocksPerSection) {
				totalChanges += changes != null ? changes.size() : 0;
			}

			this.resendLight |= totalChanges >= 64;
			if (this.skyChangedLightSectionFilter != 0 || this.blockChangedLightSectionFilter != 0) {
				broadcast(new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter, true), !this.resendLight);
				this.skyChangedLightSectionFilter = 0;
				this.blockChangedLightSectionFilter = 0;
			}

			InternalChunk storage = (InternalChunk) chunk;
			int minX = this.pos.x << 4, minZ = this.pos.z << 4;

			if (this.dynamismEnabled) {
				NetworkChunk view = storage.view();

				Iterator<Player> playerIt = getPlayers().iterator();
				while (playerIt.hasNext()) {
					Player p = playerIt.next();
					DynamicChunk dynChunk = ((InternalPlayer) p).getDynamicChunk(this.pos.x, this.pos.z);

					for (int y = 0; y < 16; y++) {
						DynamicSection dynSection = dynChunk == null ? null : dynChunk.sections[y];
						BlockChange.Builder b = BlockChange.builder(chunk, y);
						int minY = y << 4;

						ShortSet changes = this.changedBlocksPerSection[y];
						if (changes != null) {
							ShortIterator it = changes.iterator();
							while (it.hasNext()) {
								short pos = it.nextShort();
								b.add(pos, (dynSection != null && dynSection.currentlyContains(pos) ? storage : view)
										.block(minX + (pos >> 8 & 15), minY + (pos & 15), minZ + (pos >> 4 & 15)));
							}
						}

						if (dynSection != null) {
							dynSection.getChanges(b);
							dynSection.applyChanges();
						}

						b.build().sendTo(p);
					}
				}
			} else {
				BlockVolume volume = storage.isViewAvailable() ? storage.view() : storage;

				for (int y = 0; y < 16; y++) {
					ShortSet changes = this.changedBlocksPerSection[y];
					if (changes != null) {
						BlockChange.Builder b = BlockChange.builder(chunk, y);
						int minY = y << 4;

						ShortIterator it = changes.iterator();
						while (it.hasNext()) {
							short pos = it.nextShort();
							b.add(pos, volume.block(minX + (pos >> 8 & 15), minY + (pos & 15), minZ + (pos >> 4 & 15)));
						}

						b.build().sendTo(getPlayers());
					}
				}
			}

			clearChanges();
		}
	}

	@Shadow
	protected abstract void broadcast(Packet<?> param0, boolean param1);

	private Stream<Player> getPlayers() {
		return (Stream) this.playerProvider.getPlayers(this.pos, false);
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

	@Shadow
	public abstract void blockChanged(BlockPos param0);

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
		return getPlayers().map(p -> ((InternalPlayer) p).getDynamicChunk(this.pos.x, this.pos.z)).filter(Objects::nonNull);
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
