// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor {

    /**
     * Vanilla's rain and snow left unprepared while particle rain and snow replace them: working out every column for
     * drawing, only for the drawing to be canceled (MixinWeatherEffectRenderer), was a quarter of the render thread in
     * heavy rain, with a seasons mod's temperature looked up for each column on top. Only the level's own extraction:
     * a mod preparing the weather itself still gets vanilla's. The state is emptied rather than left as it was, so if
     * ours stops drawing (a failure), vanilla's starts from nothing and is prepared again from the next frame.
     */
    @WrapOperation(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"),
            require = 0)
    private void dsurround$skipVanillaWeather(WeatherEffectRenderer renderer, ClientLevel level, float partialTick, Vec3 camera,
                                              WeatherRenderState state, Operation<Void> original) {
        if (MixinHelpers.PRECIPITATION_GUARD.get(() -> MixinHelpers.precipitation().replacesWeather(), false)) {
            state.reset();
            return;
        }
        original.call(renderer, level, partialTick, camera, state);
    }
}
