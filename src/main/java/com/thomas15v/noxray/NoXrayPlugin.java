package com.thomas15v.noxray;

import com.flowpowered.math.vector.Vector3i;
import com.google.inject.Inject;
import org.spongepowered.api.Game;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.property.block.*;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.entity.living.humanoid.player.PlayerChangeClientSettingsEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Optional;

@Plugin(id = "noxray", name = "NoXray", version = "0.1", authors = "thomas15v")
public class NoXrayPlugin {

    @Inject
    private Game game;

    private static NoXrayPlugin instance;
    private static Direction[] directions = new Direction[]{Direction.DOWN, Direction.UP, Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};

    public static NoXrayPlugin getInstance(){
        return instance;
    }

    @Listener
    public void onStart(GameStartedServerEvent event){
        instance = this;
    }

    @Listener
    public void onBlockUpdate(ChangeBlockEvent.Break event){
        event.getTransactions().forEach(NoXrayPlugin::updateBlocks);
    }
    public static boolean hideBlock(Vector3i vector3i, BlockState blockState, World world){
        if (blockState.getType() == BlockTypes.AIR || blockState.getType().getProperty(MatterProperty.class).get().getValue() == MatterProperty.Matter.LIQUID) {
            return false;
        }
        Optional<HardnessProperty> hardnessProperty = blockState.getProperty(HardnessProperty.class);
        if (!hardnessProperty.isPresent()){
            return false;
        }
        if (hardnessProperty.get().getValue() < 3.0F){
            return false;
        }
        return  checkBlock(world.getBlock(vector3i.add(1,0,0)))     &&
                checkBlock(world.getBlock(vector3i.add(-1,0,0)))    &&
                checkBlock(world.getBlock(vector3i.add(0,1,0)))     &&
                checkBlock(world.getBlock(vector3i.add(0,-1,0)))    &&
                checkBlock(world.getBlock(vector3i.add(0,0,1)))     &&
                checkBlock(world.getBlock(vector3i.add(0,0,-1)));
    }

    public static void updateBlocks(Transaction<BlockSnapshot> transaction){
        Location<World> location = transaction.getOriginal().getLocation().get();
        for (Direction direction : directions) {
            updateBlock(location.getBlockRelative(direction));
        }
    }

    public  static void updateBlock(Location<World> block){
        if (block.getBlockType() == BlockTypes.AIR)
            return;
        block.getExtent().sendBlockChange(block.getPosition().toInt(), block.getBlock());
    }

    public static boolean checkBlock(BlockState blockState){
        return blockState.getType() != BlockTypes.AIR;
    }
}
