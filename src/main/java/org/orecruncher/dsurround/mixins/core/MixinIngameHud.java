package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinIngameHud {

    @Unique
    private OverlayManager dsurround_overlayManager;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/entity/ItemRenderer;)V", at = @At("RETURN"))
    public void dsurround_constructor(Minecraft minecraftClient, ItemRenderer itemRenderer, CallbackInfo ci) {
        this.dsurround_overlayManager = ContainerManager.resolve(OverlayManager.class);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER))
    public void dsurround_render(GuiGraphics guiGraphics, float f, CallbackInfo ci) {
        this.dsurround_overlayManager.render(guiGraphics, f);
    }
}
