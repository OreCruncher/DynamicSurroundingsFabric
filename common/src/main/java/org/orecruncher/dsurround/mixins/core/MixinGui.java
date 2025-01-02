package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Final
    @Shadow
    private LayeredDraw layers;

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
    public void dsurround_constructor(Minecraft minecraftClient, CallbackInfo ci) {
        // Add the overlay manager to the render layers of Gui
        OverlayManager dsurround_overlayManager = ContainerManager.resolve(OverlayManager.class);
        LayeredDraw layeredDraw = (new LayeredDraw()).add(dsurround_overlayManager::render);
        this.layers.add(layeredDraw, () -> !this.minecraft.options.hideGui);
    }
}
