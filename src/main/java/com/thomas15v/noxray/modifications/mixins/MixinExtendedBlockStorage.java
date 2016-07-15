package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtendedBlockStorage.class)
public class MixinExtendedBlockStorage {

    @Shadow
    private BlockStateContainer data;

    @Inject(method = "<init>", at = @At("RETURN") )
    public void setY (int y, boolean storeSkylight, CallbackInfo callbackInfo){
        ((InternalBlockStateContainer)data).setY(y);
    }
}
