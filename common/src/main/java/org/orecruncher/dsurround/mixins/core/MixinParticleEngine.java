package org.orecruncher.dsurround.mixins.core;

import com.google.common.collect.ImmutableList;
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
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_WATERFALL_MIST);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_FIREFLY);
            }
            listBuilder.add(entry);
        }

        RENDER_ORDER = listBuilder.build();
    }
}
