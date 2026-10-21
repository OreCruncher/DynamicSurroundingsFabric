package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.sugar.Local;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    // The fog's colour while it is tinted; the colour is set up on the render thread only
    private static final float[] dsurround$fogColor = new float[3];

    /**
     * Tints the fog's colour, after the game and (on NeoForge) any mod has set it, and clears the screen with the
     * tinted colour as the game has just done with its own. Only in the open air: under water or in lava the fog is
     * the fluid's.
     */
    @Inject(method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V", at = @At("TAIL"), require = 0)
    private static void dsurround$tintFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE)
            return;
        var rgb = dsurround$fogColor;
        rgb[0] = fogRed;
        rgb[1] = fogGreen;
        rgb[2] = fogBlue;
        // A colour half tinted when it failed is left out: the game's stands
        if (!MixinHelpers.FOG_TINT_GUARD.run(() -> MixinHelpers.fogHandler().tintFogColor(rgb, partialTick)))
            return;
        if (rgb[0] != fogRed || rgb[1] != fogGreen || rgb[2] != fogBlue) {
            fogRed = rgb[0];
            fogGreen = rgb[1];
            fogBlue = rgb[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0F);
        }
    }

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("RETURN"))
    private static void dsurround$renderFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci, @Local FogType fogType, @Local FogRenderer.FogData fogData) {

        // The sky's fog is set just before the terrain's each frame. Thickening both keeps the sky from staying clear
        // above fogged-out terrain; the game's own thick fog (the Nether, boss fog) hides the sky the same way.
        if (fogType != FogType.NONE)
            return;

        // At this point, Minecraft has already configured fog. It's possible that another
        // mixin fired and configured as well. We cannot trust the state of fogData, so
        // we interrogate the shader directly to see what was configured. (Nostalgic Tweaks
        // uses this approach.)
        var data = FogCompat.currentShaderFog(fogData.mode);

        IFogRender.EVENT.invoker().onRenderFog(data, f, g);
    }
}
