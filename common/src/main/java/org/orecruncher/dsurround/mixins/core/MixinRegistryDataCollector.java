package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.orecruncher.dsurround.eventing.ITagSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegistryDataCollector.class)
public class MixinRegistryDataCollector {
    /**
     * Called at the end of configuration (login), with the tags the server sent already applied. Whether the server
     * is in this process doesn't matter; tags arrive either way. Tags sent during play (/reload) go through
     * {@link MixinClientPacketListener}.
     */
    @Inject(method = "collectGameRegistries", at = @At("RETURN"))
    private void dsurround$tagsUpdated(ResourceProvider resourceProvider, RegistryAccess.Frozen original, boolean isMemoryConnection, CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        ITagSync.EVENT.invoker().onTagSync(cir.getReturnValue());
    }
}
