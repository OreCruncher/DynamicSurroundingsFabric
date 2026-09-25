package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import org.orecruncher.dsurround.eventing.ClientState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Shadow
    @Final
    private RegistryAccess.Frozen registryAccess;

    @Inject(method = "handleUpdateTags", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/FuelValues;vanillaBurnTimes(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/world/flag/FeatureFlagSet;)Lnet/minecraft/world/level/block/entity/FuelValues;"))
    private void dsurround$tagsUpdated(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        ClientState.TAG_SYNC_EVENT.invoker().onTagSync(this.registryAccess);
    }
}
