/*
 * Copyright (c) 2018-2025 Hugo Dupanloup (Yeregorix)
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.smoofyuniverse.mirage.impl.internal.InternalChunkAccess;
import net.smoofyuniverse.mirage.impl.internal.InternalSection;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
public class SerializableChunkDataMixin {
	private long cacheTime = 0;

	@Redirect(method = "parse", at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/LevelChunkSection"))
	private static LevelChunkSection onParse_newSection(PalettedContainer<BlockState> blocks, PalettedContainerRO<Holder<Biome>> biomes,
														LevelHeightAccessor level, RegistryAccess registry, CompoundTag tag) {
		LevelChunkSection section = new LevelChunkSection(blocks, biomes);
		if (level instanceof InternalWorld world && world.view().isEnabled())
			((InternalSection) section).view(); // lazy-init network section before data is read
		return section;
	}

	@Inject(method = "parse", at = @At("RETURN"))
	private static void onParse(LevelHeightAccessor level, RegistryAccess registry, CompoundTag tag, CallbackInfoReturnable<SerializableChunkData> cir) {
		SerializableChunkDataMixin data = (SerializableChunkDataMixin) (Object) cir.getReturnValue();
		if (data != null) {
            data.cacheTime = tag.getLongOr("MirageCacheTime", 0);
		}
	}

	@Inject(method = "copyOf", at = @At("RETURN"))
	private static void onCopy(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<SerializableChunkData> cir) {
		if (((InternalWorld) level).view().isEnabled()) {
			SerializableChunkDataMixin data = (SerializableChunkDataMixin) (Object) cir.getReturnValue();
			data.cacheTime = ((InternalChunkAccess) chunk).getCacheTime();
		}
	}

	@Inject(method = "read", at = @At("RETURN"))
	private void onRead(ServerLevel level, PoiManager pm, RegionStorageInfo info, ChunkPos pos, CallbackInfoReturnable<ProtoChunk> cir) {
		ChunkAccess chunk = cir.getReturnValue();
		if (chunk instanceof ImposterProtoChunk) {
			chunk = ((ImposterProtoChunk) chunk).getWrapped();
		}
		((InternalChunkAccess) chunk).setCacheTime(this.cacheTime);
	}

	@Inject(method = "write", at = @At("RETURN"))
	private void onWrite(CallbackInfoReturnable<CompoundTag> cir) {
		if (this.cacheTime != 0) {
			cir.getReturnValue().putLong("MirageCacheTime", this.cacheTime);
		}
	}
}
