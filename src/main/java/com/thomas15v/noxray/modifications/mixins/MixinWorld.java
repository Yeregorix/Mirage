package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.api.NetworkWorld;
import com.thomas15v.noxray.modifications.internal.InternalWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public abstract class MixinWorld implements InternalWorld {

    private NetworkWorld networkWorld = new NetworkWorld();

    @Override
    public NetworkWorld getNetworkWorld() {
        return networkWorld;
    }
}
