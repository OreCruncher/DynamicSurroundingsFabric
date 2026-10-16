package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class MixinWeatherEffectRenderer {

    /**
     * The storm's strength in place of the world's rain level where the rain and snow are set up for drawing (how
     * opaque they are). The world's level itself is left alone, so the sky, isRaining() and everything else reading it
     * are unchanged. Ticked rain is in MixinClientLevel. A dimension that draws its own weather (NeoForge) is left to
     * it before this is read.
     */
    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainLevelDrawn(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }
}
