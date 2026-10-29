package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /**
     * Starts building the frame's rain and snow on another thread as the level starts to be drawn, the camera set for
     * the frame (by update and extract, before render), so it is ready by the weather's pass (see DropMeshes).
     * Optional: without it they are built in that pass, as they were.
     */
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void dsurround$beginLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        var camera = ((GameRenderer) (Object) this).getMainCamera();
        MixinHelpers.PRECIPITATION_AHEAD_GUARD.run(() -> MixinHelpers.precipitation().beginFrame(camera, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }
}
