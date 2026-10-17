package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    // The fog's colour while it is tinted; the colour is set up on the render thread only
    @Unique
    private static final float[] dsurround$fogColor = new float[3];

    /**
     * Tints the fog's colour, after the game and (on NeoForge) any mod has set it. The screen is cleared with the fog's
     * colour, so that follows. Only in the open air: under water or in lava the fog is the fluid's.
     */
    @Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Vector4f;)V", at = @At("TAIL"), require = 0)
    private void dsurround$tintFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f color, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE)
            return;
        var rgb = dsurround$fogColor;
        rgb[0] = color.x;
        rgb[1] = color.y;
        rgb[2] = color.z;
        // A colour half tinted when it failed is left out: the game's stands
        if (MixinHelpers.FOG_TINT_GUARD.run(() -> MixinHelpers.fogHandler().tintFogColor(rgb, partialTick)))
            color.set(rgb[0], rgb[1], rgb[2], color.w);
    }
}
