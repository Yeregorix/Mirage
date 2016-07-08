package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.modifications.internal.InternalBitArray;
import net.minecraft.util.BitArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

@Mixin(BitArray.class)
public class MixinBitArray implements InternalBitArray {

    @Shadow
    private long[] longArray;
    @Shadow
    private int bitsPerEntry;
    @Shadow
    private int arraySize;

    @Override
    public BitArray copy() {
        InternalBitArray bitArray = (InternalBitArray) new BitArray(bitsPerEntry, arraySize);
        bitArray.setLongArray(Arrays.copyOf(longArray, longArray.length));
        return (BitArray) bitArray;
    }

    @Override
    public void setLongArray(long[] longArray) {
        this.longArray = longArray;
    }
}
