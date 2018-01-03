package com.thomas15v.noxray.event;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public class PlayerEventListener {

	@Listener
	public void onBlockUpdate(ChangeBlockEvent.Break event) {
		if (event.getCause().containsType(Player.class)) {
			event.getTransactions().forEach(PlayerEventListener::updateBlocks);
		}
	}

	private static void updateBlocks(Transaction<BlockSnapshot> transaction) {
		Location<World> location = transaction.getOriginal().getLocation().get();
		for (Direction direction : Direction.values()) {
			if (!direction.isSecondaryOrdinal()) {
				updateBlock(location.getBlockRelative(direction));
			}
		}
	}

	private static void updateBlock(Location<World> block) {
		if (block.getBlockType() == BlockTypes.AIR)
			return;
		block.getExtent().sendBlockChange(block.getPosition().toInt(), block.getBlock());
	}


}
