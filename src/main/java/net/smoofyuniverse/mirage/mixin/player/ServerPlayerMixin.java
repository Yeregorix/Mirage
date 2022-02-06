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

package net.smoofyuniverse.mirage.mixin.player;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.smoofyuniverse.mirage.impl.internal.InternalPlayer;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicChunk;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicSection;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements InternalPlayer {

	@Inject(method = "trackChunk", at = @At("RETURN"))
	public void onTrackChunk(ChunkPos pos, Packet<?> levelChunkPacket, Packet<?> lightUpdatePacket, CallbackInfo ci) {
		InternalWorld world = ((InternalWorld) world());
		if (world.isDynamismEnabled()) {
			DynamicChunk chunk = world.getOrCreateDynamicWorld(this).getOrCreateChunk(pos.x, pos.z);
			for (DynamicSection section : chunk.sections) {
				section.applyChanges();
				section.getCurrent().build().sendTo(this);
			}
		}
	}

	@Inject(method = "untrackChunk", at = @At("RETURN"))
	public void onUntrackChunk(ChunkPos pos, CallbackInfo ci) {
		DynamicWorld dynWorld = getDynamicWorld();
		if (dynWorld != null)
			dynWorld.removeChunk(pos.x, pos.z);
	}
}
