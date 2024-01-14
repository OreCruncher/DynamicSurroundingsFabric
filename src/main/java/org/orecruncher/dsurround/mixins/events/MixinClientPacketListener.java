package org.orecruncher.dsurround.mixins.events;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.GameUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "handleUpdateTags(Lnet/minecraft/network/protocol/common/ClientboundUpdateTagsPacket;)V", at = @At("RETURN"))
    public void dsurround_handleTagUpdates(ClientboundUpdateTagsPacket clientboundUpdateTagsPacket, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        ClientState.TAG_SYNC.raise().onTagSync(self.registryAccess());
    }

    /**
     * The player is set in the Minecraft instance from this routine. Once set the mod is considered connected
     * to the server.
     */
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("RETURN"))
    public void dsurround_handleJoin(ClientboundLoginPacket clientboundLoginPacket, CallbackInfo ci) {
        ClientState.ON_CONNECT.raise().onConnect(GameUtils.getMC());
    }
}
