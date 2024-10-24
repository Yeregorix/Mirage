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

package net.smoofyuniverse.mirage.mixin.chunk;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.smoofyuniverse.mirage.impl.internal.InternalChunkAccess;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class ChunkSerializerMixin {

	@Redirect(method = "read", at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/LevelChunkSection"))
	private static LevelChunkSection onRead_newSection(PalettedContainer<BlockState> blocks, PalettedContainerRO<Holder<Biome>> biomes,
													   ServerLevel level, PoiManager pm, RegionStorageInfo info, ChunkPos pos, CompoundTag tag) {
		LevelChunkSection section = new LevelChunkSection(blocks, biomes);
		if (((InternalWorld) level).view().isEnabled())
			((InternalSection) section).view(); // lazy-init network section before data is read
		return section;
	}

	@Inject(method = "read", at = @At("RETURN"))
	private static void onRead(ServerLevel level, PoiManager pm, RegionStorageInfo info, ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
		ChunkAccess chunk = cir.getReturnValue();
		if (chunk instanceof ImposterProtoChunk)
			chunk = ((ImposterProtoChunk) chunk).getWrapped();

		((InternalChunkAccess) chunk).setCacheTime(tag.getLong("MirageCacheTime"));
	}

	@Inject(method = "write", at = @At("RETURN"))
	private static void onWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
		if (((InternalWorld) level).view().isEnabled()) {
			cir.getReturnValue().putLong("MirageCacheTime", ((InternalChunkAccess) chunk).getCacheTime());
		}
	}
}
