package org.orecruncher.dsurround.mixins.core;

import net.minecraft.sounds.Music;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Music.class)
public class MixinMusic {

    @Shadow
    @Final
    private int minDelay;

    @Shadow
    @Final
    private int maxDelay;

    @Inject(method = "minDelay()I", at = @At("HEAD"), cancellable = true)
    public void dsurround$getMinDelay(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.musicOptions.reduceWaitTime > 0) {
            cir.setReturnValue(dsurround$calculateNewDelayThreshold(this.minDelay));
        }
    }

    @Inject(method = "maxDelay()I", at = @At("HEAD"), cancellable = true)
    public void dsurround$getMaxDelay(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.musicOptions.reduceWaitTime > 0) {
            cir.setReturnValue(dsurround$calculateNewDelayThreshold(this.maxDelay));
        }
    }

    @Unique
    private static int dsurround$calculateNewDelayThreshold(int currentThreshold) {
        var keepAmount = 100 - MixinHelpers.musicOptions.reduceWaitTime;
        return Mth.clamp((currentThreshold * keepAmount) / 100, 1, currentThreshold);
    }
}
