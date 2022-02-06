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

import net.minecraft.server.level.ServerLevel;
import net.smoofyuniverse.mirage.impl.network.NetworkWorld;
import net.smoofyuniverse.mirage.impl.network.dynamic.DynamicWorld;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends LevelMixin {
	private NetworkWorld networkWorld;
	private Map<UUID, DynamicWorld> dynamicWorlds;
	private boolean dynamismEnabled;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		this.networkWorld = new NetworkWorld(this);
		this.networkWorld.loadConfig();
		this.dynamismEnabled = this.networkWorld.isDynamismEnabled();

		if (this.dynamismEnabled)
			this.dynamicWorlds = new HashMap<>();
	}

	@Override
	public NetworkWorld view() {
		if (this.networkWorld == null)
			throw new IllegalStateException("NetworkWorld not available");
		return this.networkWorld;
	}

	@Override
	public boolean isDynamismEnabled() {
		return this.dynamismEnabled;
	}

	@Override
	public DynamicWorld getOrCreateDynamicWorld(Player player) {
		DynamicWorld dynWorld = this.dynamicWorlds.get(player.uniqueId());
		if (dynWorld == null) {
			if (!this.dynamismEnabled)
				throw new IllegalStateException();

			dynWorld = new DynamicWorld(this, player);
			dynWorld.updateCenter();
			this.dynamicWorlds.put(player.uniqueId(), dynWorld);
		}
		return dynWorld;
	}

	@Nullable
	@Override
	public DynamicWorld getDynamicWorld(UUID id) {
		return this.dynamicWorlds.get(id);
	}

	@Override
	public void removeDynamicWorld(UUID id) {
		this.dynamicWorlds.remove(id);
	}
}
