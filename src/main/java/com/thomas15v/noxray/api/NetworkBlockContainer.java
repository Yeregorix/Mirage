package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import com.thomas15v.noxray.NoXrayPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.BlockStatePaletteHashMap;
import net.minecraft.world.chunk.BlockStatePaletteLinear;
import net.minecraft.world.chunk.BlockStatePaletteResizer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.util.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class NetworkBlockContainer implements BlockStatePaletteResizer {

    private BitArray storage;
    private IBlockStatePalette palette;
    private int bits;

    private IBlockStatePalette REGISTRY_BASED_PALETTE;
    private IBlockState AIR_BLOCK_STATE;

    private int y;

    public NetworkBlockContainer(IBlockStatePalette REGISTRY_BASED_PALETTE, IBlockState AIR_BLOCK_STATE){

        this.REGISTRY_BASED_PALETTE = REGISTRY_BASED_PALETTE;
        this.AIR_BLOCK_STATE = AIR_BLOCK_STATE;
    }

    public void setBits(int bitsIn)
    {
        if (bitsIn != bits)
        {
            bits = bitsIn;

            if (bits <= 4)
            {
                bits = 4;
                this.palette = new BlockStatePaletteLinear(bits, this);
            }
            else if (bits <= 8)
            {
                this.palette = new BlockStatePaletteHashMap(bits, this);
            }
            else
            {
                this.palette = REGISTRY_BASED_PALETTE;
                bits = MathHelper.calculateLogBaseTwoDeBruijn(Block.BLOCK_STATE_IDS.size());
            }
            this.palette.idFor(AIR_BLOCK_STATE);
            this.storage = new BitArray(bits, 4096);
        }
    }

    @Override
    public int onResize(int size, IBlockState state) {
        setBits(size);
        return palette.idFor(state);
    }

    public void set(int index, IBlockState state)
    {
        /*int y = (index >> 8) + this.y;
        int z = ((index ^ (y << 8)) >> 4) + 16 * chunkZ;
        int x = index ^ ((y << 8) + (z << 4)) + 16 * chunkX;*/
        int i = palette.idFor(state);
        storage.setAt(index, i);
    }

    public void set(int x, int y, int z, IBlockState blockState){
        this.set(getIndex(x, y, z), blockState);
    }

    private static int getIndex(int x, int y, int z)
    {
        return y << 8 | z << 4 | x;
    }

    public int size() {
        return 1 + this.palette.getSerializedState() + PacketBuffer.getVarIntSize(this.storage.size()) + this.storage.getBackingLongArray().length * 8;
    }

    public void write(PacketBuffer buf){
        buf.writeByte(this.bits);
        this.palette.write(buf);
        buf.writeLongArray(storage.getBackingLongArray());
    }

    public void setY(int y) {
        this.y = y;
    }

    public BlockState get(Vector3i vector3i){
        return (BlockState) get(vector3i.getX(), vector3i.getY(), vector3i.getZ());
    }

    public IBlockState get(int x, int y, int z)
    {
        return this.get(getIndex(x, y, z));
    }

    protected IBlockState get(int index)
    {
        IBlockState iblockstate = this.palette.getBlockState(this.storage.getAt(index));
        return iblockstate == null ? AIR_BLOCK_STATE : iblockstate;
    }

    /**
     * Obfuscates the blocks that are inside a chunk. Blocks on the chunkbored are not getting obfuscated here since we don't wanna trigger a chunkread.
     * @param chunk
     */
    public void obfuscateInnerBlocks(NetworkChunk chunk) {
        final long startTime = System.currentTimeMillis();
        BlockModifier blockModifier = NoXrayPlugin.getInstance().getBlockModifier();
        Predicate<BlockState> filter = blockModifier.getFilter();

        for (int y = 0; y < 16; y++) {
            for (int z = 1; z < 15; z++) {
                for (int x = 1; x < 15; x++) {
                    BlockState blockState = (BlockState) get(x,y,z);
                    if (blockState == AIR_BLOCK_STATE || blockState == Blocks.BEDROCK || !filter.test(blockState)) {
                        continue;
                    }
                    Vector3i vector3i = new Vector3i(x,y,z);
                    IBlockState response = (IBlockState) blockModifier.handleBlock(blockState, vector3i, null /*getSurrounding(vector3i, chunk)*/);
                    if (response != blockState) {
                        set(x, y, z, response);
                    }
                }
            }
        }
        final long time = System.currentTimeMillis() - startTime;
        if (time > 5){
            System.out.println(time);
        }
    }

    public List<BlockState> getSurrounding(Vector3i vector3i, NetworkChunk networkChunk){
        List<BlockState> blockStates = new ArrayList<>();
        try {
            blockStates.add(networkChunk.get(vector3i.add(1,0,0)));
            blockStates.add(networkChunk.get(vector3i.sub(1,0,0)));
            if (vector3i.getY() != 256){
                blockStates.add(networkChunk.get(vector3i.add(0,1,0)));
            }
            if (vector3i.getY() != 0){
                blockStates.add(networkChunk.get(vector3i.sub(0,1,0)));
            }
            blockStates.add(networkChunk.get(vector3i.add(0,0,1)));
            blockStates.add(networkChunk.get(vector3i.sub(0,0,1)));
        }catch (IllegalArgumentException e){
            System.out.println(vector3i);
            e.printStackTrace();
            System.exit(-1);
        }

        return blockStates;
    }

    public void ObfuscateOutSideBlocks(NetworkChunk networkChunk, NetworkChunk[] networkChunks) {

    }
}
