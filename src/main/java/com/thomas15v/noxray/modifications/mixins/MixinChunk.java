/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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

package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.api.NetworkBlockContainer;
import com.thomas15v.noxray.api.NetworkChunk;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import com.thomas15v.noxray.modifications.internal.InternalChunk;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Chunk.class)
public class MixinChunk implements InternalChunk {

	@Shadow
	@Final
	public int x;
	@Shadow
	@Final
	public int z;
	@Shadow
	@Final
	private World world;
	@Shadow
	@Final
	private ExtendedBlockStorage[] storageArrays;

	private NetworkChunk networkChunk;

	@Override
	public void obfuscate() {
		if (this.networkChunk == null) {
			if (this.world.getWorldType().getId() != -1 && this.world.getWorldType().getId() != 1 && this.networkChunk == null) {
				NetworkBlockContainer[] blockContainers = new NetworkBlockContainer[this.storageArrays.length];
				for (int i = 0; i < this.storageArrays.length; i++) {
					if (this.storageArrays[i] != null) {
						blockContainers[i] = ((InternalBlockStateContainer) this.storageArrays[i].getData()).getBlockContainer();
					}
				}
				this.networkChunk = new NetworkChunk(blockContainers, (org.spongepowered.api.world.Chunk) this);
				((InternalWorld) this.world).getNetworkWorld().addChunk(this.networkChunk);
				this.networkChunk.obfuscate();
			}
		}
	}
}
