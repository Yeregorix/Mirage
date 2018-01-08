/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts, 2018 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.noxray.modifications.mixins;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.smoofyuniverse.noxray.api.NetworkChunk;
import net.smoofyuniverse.noxray.modifications.internal.InternalBlockStateContainer;
import net.smoofyuniverse.noxray.modifications.internal.InternalChunk;
import net.smoofyuniverse.noxray.modifications.internal.NetworkBlockContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Chunk.class)
public class MixinChunk implements InternalChunk {
	@Shadow
	@Final
	private World world;
	@Shadow
	@Final
	private ExtendedBlockStorage[] storageArrays;

	private NetworkChunk networkChunk;

	@Override
	public NetworkChunk getNetworkChunk() {
		if (this.networkChunk == null) {
			WorldType type = this.world.getWorldType();
			if (type != WorldType.FLAT && type != WorldType.DEBUG_ALL_BLOCK_STATES) {
				NetworkBlockContainer[] containers = new NetworkBlockContainer[this.storageArrays.length];
				for (int i = 0; i < this.storageArrays.length; i++) {
					if (this.storageArrays[i] != null)
						containers[i] = ((InternalBlockStateContainer) this.storageArrays[i].getData()).getNetworkBlockContainer();
				}
				this.networkChunk = new NetworkChunk(containers, (org.spongepowered.api.world.Chunk) this);
			}
		}
		return this.networkChunk;
	}
}