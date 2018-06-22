package net.smoofyuniverse.mirage.mixin;

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
