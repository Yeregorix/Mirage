package com.thomas15v.noxray.api;

import com.flowpowered.math.vector.Vector3i;
import com.thomas15v.noxray.NoXrayPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BitArray;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.BlockStatePaletteHashMap;
import net.minecraft.world.chunk.BlockStatePaletteLinear;
import net.minecraft.world.chunk.BlockStatePaletteResizer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

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

    public void obfuscateBlocks(NetworkChunk chunk) {
        BlockModifier blockModifier = NoXrayPlugin.getInstance().getBlockModifier();
        Predicate<BlockState> filter = blockModifier.getFilter();
        int maxY =  16 + this.y;
        for (int y = this.y; y < maxY; y++) {
            for (int z = 0; z < 15; z++) {
                for (int x = 0; x < 15; x++) {
                    obfuscateBlock(filter, x,y,z,chunk, blockModifier);
                }
            }
        }
    }

    /*public void obfuscateOutSideBlocks(NetworkChunk targetChunk, NetworkChunk sideChunk, Direction direction) {
        BlockModifier blockModifier = NoXrayPlugin.getInstance().getBlockModifier();
        Predicate<BlockState> filter = blockModifier.getFilter();
        int maxY =  16 + this.y;
        for (int y = this.y; y < maxY; y++) {
            for (int xz = 1; xz < 15; xz++) {
                int x = direction.getX() != 0 ? direction.getX() > 0 ? 15 : 0 : xz;
                int z = direction.getZ() != 0 ? direction.getZ() > 0 ? 15 : 0 : xz;
                obfuscateOutSideBlock(filter, x, y, z,direction, targetChunk, sideChunk,blockModifier);
                for (NetworkBlockContainer blockContainer : sideChunk.getBlockStateContainers()) {
                    if (blockContainer != null) {
                        blockContainer.obfuscateOutSideBlock(filter, (x + direction.getX()) & 15, y,
                                (z + direction.getZ()) & 15 + direction.getZ(), direction, targetChunk, sideChunk, blockModifier);
                    }
                }
            }
        }
    }*/

    private void obfuscateBlock(Predicate<BlockState> filter, int x, int y, int z, NetworkChunk chunk, BlockModifier blockModifier){
        BlockState blockState = chunk.get(x, y, z);
        if (filter.test(blockState)) {
            Vector3i vector3i = new Vector3i(x, y, z);
            BlockState response = blockModifier.handleBlock(blockState, vector3i, getSurrounding(vector3i, chunk));
            if (response != blockState) {
                chunk.set(x, y, z, response);
            }
        }
    }

    /*private void obfuscateOutSideBlock(Predicate<BlockState> filter, int x, int y, int z, Direction direction,  NetworkChunk targetChunk,NetworkChunk sideChunk, BlockModifier blockModifier){
        BlockState blockState = targetChunk.get(x, y, z);
        if (filter.test(blockState)) {
            Vector3i targetLocation = new Vector3i(x, y, z);
            List<BlockState> blockStates = getSurrounding(targetLocation, targetChunk);
            Vector3i moduloLocation = Util.moduloVector(direction.getRelative(targetLocation), 16);
            BlockState sideState = sideChunk.get(moduloLocation);
            if (sideState == null) {
                System.out.println(blockState + " " + direction + " " + moduloLocation + " " + targetLocation + blockStates);
            }
            blockStates.add(sideState);
            BlockState response = blockModifier.handleBlock(blockState, targetLocation, blockStates);
            if (response != blockState) {
                targetChunk.set(x, y, z, response);
            }
        }
    }*/

    //public void obfuscateCorners(NetworkChunk networkChunk, )

    private final static org.spongepowered.api.util.Direction[] directions = new org.spongepowered.api.util.Direction[]{org.spongepowered.api.util.Direction.EAST,
            org.spongepowered.api.util.Direction.NORTH, org.spongepowered.api.util.Direction.SOUTH, org.spongepowered.api.util.Direction.WEST};

    public List<BlockState> getSurrounding(Vector3i vector3i, Location<World> location){
        List<BlockState> blockStates = new ArrayList<BlockState>(){
            @Override
            public boolean add(BlockState blockState) {
                if(blockState != null) {
                    return super.add(blockState);
                }
                return false;
            }
        };

        if (vector3i.getY() != 256){
            blockStates.add(location.getRelative(org.spongepowered.api.util.Direction.UP).getBlock());
        }
        if (vector3i.getY() != 0){
            blockStates.add(location.getRelative(org.spongepowered.api.util.Direction.DOWN).getBlock());
        }
        for (org.spongepowered.api.util.Direction direction : directions) {
                blockStates.add(location.getRelative(direction));
        }
        if (blockStates.size() < 5){
            System.out.println("WARNING FAULTY CODE: " + blockStates + " " + vector3i);
        }
        return blockStates;
    }

}
