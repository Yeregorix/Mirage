package com.thomas15v.noxray.modifications.internal;

import com.thomas15v.noxray.api.NetworkWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

public interface InternalWorld {

    IBlockState getBlockStateWithoutLoading(BlockPos pos);

    NetworkWorld getNetworkWorld();
}
