package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.orecruncher.dsurround.gui.debug.DiagnosticsHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinIngameHud {

    private DiagnosticsHud hud;

    @Inject(method = "<init>(Lnet/minecraft/client/MinecraftClient;)V", at = @At("RETURN"))
    public void constructor(MinecraftClient minecraftClient, CallbackInfo ci) {
        hud = new DiagnosticsHud(minecraftClient);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusEffectOverlay(Lnet/minecraft/client/util/math/MatrixStack;)V", shift = At.Shift.AFTER))
    public void render(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        hud.render(matrices);
    }
}
