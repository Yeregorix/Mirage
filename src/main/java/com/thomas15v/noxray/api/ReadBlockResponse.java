package com.thomas15v.noxray.api;

import org.spongepowered.api.block.BlockState;

public class ReadBlockResponse {

	private BlockState blockState;
	private boolean playerSpecific;

	public ReadBlockResponse(BlockState blockState) {
		this(blockState, false);
	}

	public ReadBlockResponse(BlockState blockState, boolean playerSpecific) {
		this.blockState = blockState;
		this.playerSpecific = playerSpecific;
	}

	/**
	 * @returns the new block state
	 */
	public BlockState getBlockState() {
		return blockState;
	}

	/**
	 * @returns if this block should be handled for players
	 */
	public boolean isPlayerSpecific() {
		return playerSpecific;
	}

	public ReadBlockResponse setBlockState(BlockState blockState) {
		this.blockState = blockState;
		return this;
	}
}
