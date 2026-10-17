package org.orecruncher.fabric.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {

    @Shadow
    private boolean isHidden;

    @Unique
    private OverlayManager overlayManager;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
    public void dsurround$constructor(Minecraft minecraftClient, CallbackInfo ci) {
        this.overlayManager = ContainerManager.resolve(OverlayManager.class);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target="Lnet/minecraft/client/gui/Hud;extractSleepOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    public void dsurround$render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!this.isHidden) {
            this.overlayManager.render(graphics, deltaTracker);
        }
    }
}
