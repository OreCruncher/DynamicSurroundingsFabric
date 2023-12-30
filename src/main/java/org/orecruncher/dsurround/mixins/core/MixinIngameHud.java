package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.item.ItemRenderer;
import org.orecruncher.dsurround.gui.hud.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinIngameHud {

    @Unique
    private OverlayManager dsurround_overlayManager;

    @Inject(method = "<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/render/item/ItemRenderer;)V", at = @At("RETURN"))
    public void dsurround_constructor(MinecraftClient minecraftClient, ItemRenderer itemRenderer, CallbackInfo ci) {
        this.dsurround_overlayManager = ContainerManager.resolve(OverlayManager.class);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;)V", shift = At.Shift.AFTER))
    public void dsurround_render(DrawContext context, float tickDelta, CallbackInfo ci) {
        this.dsurround_overlayManager.render(context);
    }
}
