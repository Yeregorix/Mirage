/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.mixin.world;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer extends MixinWorld {
	private NetworkWorld networkWorld;

	@Inject(method = "init", at = @At("RETURN"))
	public void onInit(CallbackInfoReturnable<World> ci) {
		this.networkWorld = new NetworkWorld(this);

		Mirage.LOGGER.info("Loading configuration for world " + getName() + " ..");
		try {
			this.networkWorld.loadConfig();
		} catch (Exception e) {
			Mirage.LOGGER.error("Failed to load configuration for world " + getName(), e);
		}
	}

	@Override
	public NetworkWorld getView() {
		if (this.networkWorld == null)
			throw new IllegalStateException("NetworkWorld not available");
		return this.networkWorld;
	}
}
