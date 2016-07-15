package com.thomas15v.noxray.modifications.mixins;

import com.thomas15v.noxray.modifications.internal.InternalPacketChunkData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerChunkMapEntry.class)
public class MixinPlayerChunkMapEntry {

    /*@Redirect(method = "sentToPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    public void sentToPlayers(NetHandlerPlayServer handler, Packet packet){
        setPlayerForPacketChunkData(handler, packet);
    }

    @Redirect(method = "sendNearbySpecialEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    public void sendNearbySpecialEntities(NetHandlerPlayServer handler, Packet packet){
        setPlayerForPacketChunkData(handler, packet);
    }

    /*@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    public void updateExtra(NetHandlerPlayServer handler, Packet packet){
        setPlayerForPacketChunkData(player, packet);
    }

    private void setPlayerForPacketChunkData(NetHandlerPlayServer handler, Packet packet){
        ((InternalPacketChunkData)packet).setPlayer(handler.playerEntity);
        handler.sendPacket(packet);
    }*/
}
