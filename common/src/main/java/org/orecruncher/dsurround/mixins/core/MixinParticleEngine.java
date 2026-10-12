package org.orecruncher.dsurround.mixins.core;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Shadow
    @Final
    @Mutable
    private static List<ParticleRenderType> RENDER_ORDER;

    @Inject(method="<clinit>", at = @At("TAIL"))
    private static void dsurround$registerRenderTypes(CallbackInfo ci) {

        var listBuilder = ImmutableList.<ParticleRenderType>builder();

        for( var entry : RENDER_ORDER ) {
            if (entry == ParticleRenderType.CUSTOM) {
                // Insert our custom types before CUSTOM. By default, CUSTOM is at the end of the render
                // ordering list.
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_MIST);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_FIREFLY);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_FLAME);
            }
            listBuilder.add(entry);
        }

        RENDER_ORDER = listBuilder.build();
    }

    /**
     * Puts the usual blend back once the particles are drawn: flames are drawn adding their light, and a render type
     * can't undo that itself. Whatever is drawn next and turns blending on without setting how (vanilla's rain, for
     * one) would otherwise add too.
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("TAIL"))
    private void dsurround$restoreBlend(CallbackInfo ci) {
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The same, for NeoForge, whose level renderer draws particles through a render method of its own. Vanilla has no
     * such method, so it isn't remapped (NeoForge runs on these names) and isn't required (Fabric hasn't got it):
     * otherwise the common module's build warns it can't be found.
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void dsurround$restoreBlendNeoForge(CallbackInfo ci) {
        RenderSystem.defaultBlendFunc();
    }
}
