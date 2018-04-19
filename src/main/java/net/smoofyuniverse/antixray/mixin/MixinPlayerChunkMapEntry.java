/*
 * Copyright (c) 2018 Hugo Dupanloup (Yeregorix)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.smoofyuniverse.antixray.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.smoofyuniverse.antixray.impl.internal.InternalChunk;
import net.smoofyuniverse.antixray.impl.internal.InternalMultiBlockChange;
import net.smoofyuniverse.antixray.impl.internal.InternalWorld;
import net.smoofyuniverse.antixray.impl.network.ChunkChangeListener;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerChunkMapEntry.class)
public abstract class MixinPlayerChunkMapEntry implements ChunkChangeListener {

	@Shadow
	private Chunk chunk;

	@Shadow
	private int changes;

	@Shadow
	private int changedSectionFilter;

	@Inject(method = "sendToPlayers", at = @At(value = "FIELD", target = "Lnet/minecraft/server/management/PlayerChunkMapEntry;sentToPlayers:Z", opcode = Opcodes.PUTFIELD))
	public void onSendToPlayers(CallbackInfoReturnable<Boolean> ci) {
		if (((InternalChunk) this.chunk).isViewAvailable())
			((InternalChunk) this.chunk).getView().setListener(this);
	}

	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), require = 2)
	public IBlockState onGetBlockState(WorldServer world, BlockPos pos) {
		return (IBlockState) ((InternalWorld) world).getApplicable().getBlock(pos.getX(), pos.getY(), pos.getZ());
	}

	@Redirect(method = "update", at = @At(value = "NEW", target = "net/minecraft/network/play/server/SPacketMultiBlockChange"))
	public SPacketMultiBlockChange newSPacketMultiBlockChange(int changes, short[] changedBlocks, Chunk chunk) {
		SPacketMultiBlockChange packet = new SPacketMultiBlockChange();
		((InternalMultiBlockChange) packet).fastInit(changes, changedBlocks, chunk);
		return packet;
	}

	@Override
	public void addChange(int x, int y, int z) {
		blockChanged(x, y, z);
	}

	@Shadow
	public abstract void blockChanged(int x, int y, int z);

	@Override
	public void sendChanges() {
		update();
	}

	@Shadow
	public abstract void update();

	@Override
	public void clearChanges() {
		this.changes = 0;
		this.changedSectionFilter = 0;
	}
}
