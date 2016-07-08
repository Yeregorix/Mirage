package com.thomas15v.noxray.modifications.mixins;

import com.flowpowered.math.vector.Vector3i;
import com.thomas15v.noxray.NoXrayPlugin;
import com.thomas15v.noxray.modifications.internal.InternalBitArray;
import com.thomas15v.noxray.modifications.internal.InternalBlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockStateContainer.class)
public abstract class MixinBlockStateContainer implements InternalBlockStateContainer {

    @Shadow
    private int bits;
    @Shadow
    protected BitArray storage;
    @Shadow
    protected IBlockStatePalette palette;
    private int y;
    @Shadow
    public abstract IBlockState get(int x, int y, int z);
    @Shadow
    private static IBlockStatePalette REGISTRY_BASED_PALETTE;
    @Shadow
    protected static IBlockState AIR_BLOCK_STATE;

    private BitArray modifiedStorage;

    @Override
    public void updateModified(Chunk chunk)
    {
        int blockx = chunk.xPosition * 16;
        int blockz = chunk.zPosition * 16;
        int blocky = getY();
        BitArray data = ((InternalBitArray) storage).copy();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if(NoXrayPlugin.getInstance().hideBlock(new Vector3i(x + blockx, blocky + y, z + blockz ), (BlockState) get(x,y,z), (World) chunk.getWorld())){
                        setBitarray(x,y,z, Blocks.STONE.getDefaultState(), data);
                    }
                }
            }
        }
        modifiedStorage = data;
    }

    @Override
    public void writeModified(PacketBuffer buf) {
        buf.writeByte(this.bits);
        this.palette.write(buf);
        buf.writeLongArray(modifiedStorage.getBackingLongArray());
    }

    @Override
    public int modifiedSize() {
        return 1 + this.palette.getSerializedState() + PacketBuffer.getVarIntSize(this.modifiedStorage.size()) + this.modifiedStorage.getBackingLongArray().length * 8;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int getY() {
        return y;
    }

    public void setBitarray(int x, int y, int z, IBlockState state, BitArray bitArray)
    {
        try {
            this.setBitarray(getLocalIndex(x, y, z), state, bitArray);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setBitarray(int index, IBlockState state, BitArray bitArray)
    {
        int i = this.palette.idFor(state);
        bitArray.setAt(index, i);


    }

    private static int getLocalIndex(int x, int y, int z)
    {
        return y << 8 | z << 4 | x;
    }
}
