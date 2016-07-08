package com.thomas15v.noxray.modifications.internal;

import net.minecraft.util.BitArray;

public interface InternalBitArray {

    BitArray copy();

    void setLongArray(long[] longArray);
}
