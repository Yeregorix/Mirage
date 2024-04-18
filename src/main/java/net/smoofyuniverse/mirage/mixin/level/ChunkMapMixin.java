/*
 * Copyright (c) 2018-2024 Hugo Dupanloup (Yeregorix)
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

import com.mojang.datafixers.util.Either;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.level.ChunkHolder.ChunkLoadingFailure;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter.Message;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.volume.ChunkView.State;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicWorld;
import net.smoofyuniverse.mirage.mixin.chunk.ChunkStorageMixin;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin extends ChunkStorageMixin {
	@Shadow
	@Final
	ServerLevel level;
	@Shadow
	@Final
	private ProcessorHandle<Message<Runnable>> mainThreadMailbox;
	@Shadow
	@Final
	private AtomicInteger tickingGenerated;

	@Inject(method = "save", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;"))
	public void beforeSerialize(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (chunk instanceof InternalChunk) {
			try {
				InternalChunk internalChunk = (InternalChunk) chunk;
				if (internalChunk.isViewAvailable()) {
					internalChunk.view().saveToCacheLater();
					chunk.setUnsaved(false);
				}
			} catch (Exception e) {
				Mirage.LOGGER.error("Failed to serialize a network chunk for caching", e);
			}
		}
	}

	@Inject(method = "save", at = @At(value = "INVOKE", shift = Shift.AFTER,
			target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"))
	public void afterWrite(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (chunk instanceof InternalChunk) {
			ChunkPos pos = chunk.getPos();
			NetworkWorld world = ((InternalChunk) chunk).world().view();

			this.writeFuture.thenRun(() -> {
				try {
					world.savePendingChunk(pos.x, pos.z);
				} catch (Exception e) {
					Mirage.LOGGER.error("Failed to save a pending network chunk", e);
				}
			});
		}
	}

	@Inject(method = "move", at = @At("HEAD"))
	public void onMove(ServerPlayer player, CallbackInfo ci) {
		if (((InternalWorld) this.level).isDynamismEnabled()) {
			DynamicWorld world = ((InternalPlayer) player).getDynamicWorld();
			if (world != null)
				world.updateCenter((Player) player);
		}
	}

	@Redirect(method = "prepareTickingChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	public CompletableFuture<Void> onChunkSend(CompletableFuture<Either<LevelChunk, ChunkLoadingFailure>> instance,
											   Consumer<?> consumer, Executor executor, ChunkHolder holder) {
		return instance.thenAcceptAsync((chunkOrFail) -> chunkOrFail.ifLeft((levelChunk) -> {
			this.tickingGenerated.getAndIncrement();

			InternalChunk chunk = (InternalChunk) levelChunk;
			if (chunk.isViewAvailable()) {
				NetworkChunk view = chunk.view();
				view.obfuscate();

				/*if (chunk.getView().getState() != State.OBFUSCATED)
					return false;*/

				ChunkChangeListener listener = (ChunkChangeListener) holder;
				listener.setDynamismEnabled(view.isDynamismEnabled());
				view.setListener(listener);
			}

			MutableObject<ClientboundLevelChunkWithLightPacket> packet = new MutableObject<>();
			getPlayers(holder.getPos(), false).forEach(p -> playerLoadedChunk(p, packet, levelChunk));
		}), (task) -> this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, task)));
	}

	@Shadow
	public abstract List<ServerPlayer> getPlayers(ChunkPos param0, boolean param1);

	@Shadow
	protected abstract void playerLoadedChunk(ServerPlayer param0, MutableObject<ClientboundLevelChunkWithLightPacket> param1, LevelChunk param2);

	@Inject(method = "playerLoadedChunk", at = @At(value = "INVOKE", shift = Shift.AFTER,
			target = "Lnet/minecraft/server/level/ServerPlayer;trackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/network/protocol/Packet;)V"))
	public void afterChunkSent(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk levelChunk, CallbackInfo ci) {
		InternalChunk chunk = (InternalChunk) levelChunk;
		if (chunk.isViewAvailable() && chunk.view().state() != State.OBFUSCATED) {
			ChunkPos pos = levelChunk.getPos();
			Mirage.LOGGER.warn("Chunk {} {} has been sent without obfuscation.", pos.x, pos.z);
		}
	}
}
