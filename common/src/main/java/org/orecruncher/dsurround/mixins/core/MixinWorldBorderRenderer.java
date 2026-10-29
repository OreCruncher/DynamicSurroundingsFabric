package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world border is drawn last in the level's weather pass (see MixinLevelRenderer), after the weather: what is
 * drawn after the weather and before the border goes here.
 */
@Mixin(WorldBorderRenderer.class)
public class MixinWorldBorderRenderer {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void dsurround$beforeWorldBorder(WorldBorderRenderState state, Vec3 camera, double renderDistance, double depthFar, CallbackInfo ci) {
        if (!MixinHelpers.inLevelWeatherPass)
            return;
        // The ground fog with Fabulous graphics, after the weather (see GroundFogHandler.renderAfterWeather) and
        // before the world border, which would otherwise be hidden behind the depth the mist writes
        MixinHelpers.GROUND_FOG_GUARD.run(() -> MixinHelpers.groundFog().renderAfterWeather());
        // The lightning, after the ground fog: with Fabulous graphics it writes depth, which would hide weather drawn
        // after it behind its glow (see LightningHandler)
        MixinHelpers.LIGHTNING_GUARD.run(() -> MixinHelpers.lightning().render());
    }
}
