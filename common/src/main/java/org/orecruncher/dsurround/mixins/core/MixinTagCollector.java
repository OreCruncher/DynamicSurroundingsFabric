package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.TagCollector;
import net.minecraft.core.RegistryAccess;
import org.orecruncher.dsurround.eventing.ITagSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TagCollector.class)
public class MixinTagCollector {
    @Inject(method = "updateTags(Lnet/minecraft/core/RegistryAccess;Z)V", at = @At("TAIL"))
    private void dsurround$tagsUpdated(RegistryAccess registryAccess, boolean local, CallbackInfo ci) {
        if (local)
            ITagSync.EVENT.invoker().onTagSync(registryAccess);
    }
}
