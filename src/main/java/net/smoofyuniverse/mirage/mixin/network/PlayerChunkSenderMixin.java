/*
 * Copyright (c) 2024 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.mixin.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicChunk;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicWorld;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    @Inject(method = "sendChunk", at = @At("RETURN"))
    private static void afterChunkSent(ServerGamePacketListenerImpl packetListener, ServerLevel level, LevelChunk levelChunk, CallbackInfo ci) {
        ChunkPos pos = levelChunk.getPos();

        InternalChunk chunk = (InternalChunk) levelChunk;
        if (chunk.isViewAvailable() && chunk.view().state() != ChunkView.State.OBFUSCATED) {
            Mirage.LOGGER.warn("Chunk {} {} has been sent without obfuscation.", pos.x, pos.z);
        }

        InternalWorld world = ((InternalWorld) level);
        if (world.isDynamismEnabled()) {
            ServerPlayer player = packetListener.player;
            DynamicChunk dynChunk = world.getOrCreateDynamicWorld((Player) player).getOrCreateChunk(pos.x, pos.z);
            for (DynamicSection section : dynChunk.sections) {
                if (section != null) {
                    section.applyChanges();
                    section.getCurrent().sendTo(player);
                }
            }
        }
    }

    @Inject(method = "dropChunk", at = @At("RETURN"))
    private void onChunkDrop(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        DynamicWorld dynWorld = ((InternalPlayer) player).getDynamicWorld();
        if (dynWorld != null) {
            dynWorld.removeChunk(pos.x, pos.z);
        }
    }
}
