package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.orecruncher.dsurround.eventing.ILightUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public class MixinClientChunkCache {

    /**
     * The light engine calls this for each section whose light it has changed, so vanilla draws the section again
     */
    @Inject(method = "onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V", at = @At("TAIL"))
    public void dsurround$onLightUpdate(LightLayer layer, SectionPos section, CallbackInfo ci) {
        ILightUpdate.EVENT.invoker().onLightUpdate(layer, section);
    }
}
