package org.orecruncher.dsurround.mixins.events;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import org.orecruncher.dsurround.lib.platform.events.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "handleUpdateTags(Lnet/minecraft/network/protocol/common/ClientboundUpdateTagsPacket;)V", at = @At("RETURN"))
    public void dsurround_handleTagUpdates(ClientboundUpdateTagsPacket clientboundUpdateTagsPacket, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        ClientState.TAG_SYNC.raise(new ClientState.TagSyncEvent(self.registryAccess()));
    }
}
