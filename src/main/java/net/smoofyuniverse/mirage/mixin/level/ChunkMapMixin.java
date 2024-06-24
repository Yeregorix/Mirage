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

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkChunk;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import net.smoofyuniverse.mirage.impl.network.change.ChunkChangeListener;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicWorld;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Shadow
	@Final
	ServerLevel level;

	@Inject(method = "save", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;"))
	public void beforeChunkSerialize(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
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

	@Redirect(method = "save", at = @At(value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;exceptionallyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	public CompletableFuture<Void> onChunkWrite(CompletableFuture<Void> future, Function<Throwable, Void> fn, Executor executor, ChunkAccess chunk) {
		if (chunk instanceof InternalChunk) {
			ChunkPos pos = chunk.getPos();
			NetworkWorld world = ((InternalChunk) chunk).world().view();

			future.thenRun(() -> {
				try {
					world.savePendingChunk(pos.x, pos.z);
				} catch (Exception e) {
					Mirage.LOGGER.error("Failed to save a pending network chunk", e);
				}
			});
		}
		return future.exceptionallyAsync(fn, executor);
	}

	@Inject(method = "move", at = @At("HEAD"))
	public void onPlayerMove(ServerPlayer player, CallbackInfo ci) {
		if (((InternalWorld) this.level).isDynamismEnabled()) {
			DynamicWorld world = ((InternalPlayer) player).getDynamicWorld();
			if (world != null)
				world.updateCenter((Player) player);
		}
	}

	@Inject(method = "lambda$prepareTickingChunk$42", at = @At("HEAD"))
	public void onChunkPrepare(ChunkHolder holder, LevelChunk levelChunk, CallbackInfo ci) {
		InternalChunk chunk = (InternalChunk) levelChunk;
		if (chunk.isViewAvailable()) {
			NetworkChunk view = chunk.view();
			ChunkChangeListener listener = (ChunkChangeListener) holder;
			listener.setDynamismEnabled(view.isDynamismEnabled());
			view.setListenerCandidate(listener);
		}
	}

	@Inject(method = "onChunkReadyToSend", at = @At("HEAD"))
	public void onChunkSend(LevelChunk levelChunk, CallbackInfo ci) {
		InternalChunk chunk = (InternalChunk) levelChunk;
		if (chunk.isViewAvailable()) {
			NetworkChunk view = chunk.view();
			view.obfuscate();
			view.setListener();
		}
	}
}
