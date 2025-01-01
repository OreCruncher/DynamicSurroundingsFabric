package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.orecruncher.dsurround.eventing.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegistryDataCollector.class)
public class MixinRegistryDataCollector {
    @Inject(method = "collectGameRegistries(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/core/RegistryAccess$Frozen;Z)Lnet/minecraft/core/RegistryAccess$Frozen;", at = @At("TAIL"))
    private void dsurround_tagsUpdated(ResourceProvider resourceProvider, RegistryAccess.Frozen registryManager, boolean inMemory, CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        if (inMemory)
            ClientState.TAG_SYNC.raise().onTagSync(registryManager);
    }
}
