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

package net.smoofyuniverse.mirage.mixin.level;

import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.smoofyuniverse.mirage.api.volume.ChunkStorage;
import net.smoofyuniverse.mirage.impl.internal.InternalChunk;
import net.smoofyuniverse.mirage.impl.internal.InternalWorld;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Mixin(Level.class)
public abstract class LevelMixin implements InternalWorld, LevelAccessor {

	@Shadow
	@Final
	public boolean isClientSide;

	@Override
	public NetworkWorld view() {
		throw new UnsupportedOperationException();
	}

	@Nullable
	@Override
	public InternalChunk opaqueChunk(int x, int z) {
		return (InternalChunk) getChunkSource().getChunkNow(x, z);
	}

	@Override
	public boolean isChunkLoaded(int x, int z) {
		return opaqueChunk(x, z) != null;
	}

	@Override
	public Stream<InternalChunk> loadedOpaqueChunks() {
		if (this.isClientSide)
			return Stream.empty();

		return (Stream) Streams.stream(((ServerChunkCache) getChunkSource()).chunkMap.getChunks()).map(holder -> {
			Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> chunkOrFail = holder.getFullChunkFuture().getNow(null);
			return chunkOrFail == null ? null : chunkOrFail.left().orElse(null);
		}).filter(Objects::nonNull);
	}

	@Override
	public boolean isExposed(int x, int y, int z) {
		InternalChunk chunk = opaqueChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isExposed(x, y, z);
	}

	@Override
	public boolean isOpaque(int x, int y, int z) {
		InternalChunk chunk = opaqueChunk(x >> 4, z >> 4);
		return chunk != null && chunk.isOpaque(x, y, z);
	}

	@Override
	public boolean isOpaqueChunkLoaded(int x, int y, int z) {
		return isChunkLoaded(x, z);
	}

	@Override
	public Optional<ChunkStorage> opaqueChunk(int x, int y, int z) {
		return Optional.ofNullable(opaqueChunk(x, z));
	}

	@Override
	public Optional<ChunkStorage> opaqueChunkAt(int x, int y, int z) {
		return opaqueChunk(x >> 4, 0, z >> 4);
	}
}
