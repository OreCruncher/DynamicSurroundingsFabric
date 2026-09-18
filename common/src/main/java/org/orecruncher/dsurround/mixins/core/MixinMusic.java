package org.orecruncher.dsurround.mixins.core;

import net.minecraft.sounds.Music;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "getMinDelay()I", at = @At("HEAD"), cancellable = true)
    public void dsurround$getMinDelay(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.musicOptions.reduceWaitTime != 0) {
            var keepAmount = 100 - MixinHelpers.musicOptions.reduceWaitTime;
            var newWaitTime = Mth.clamp((this.minDelay * keepAmount) / 100, 1, this.minDelay);
            cir.setReturnValue(newWaitTime);
        }
    }

    @Inject(method = "getMaxDelay()I", at = @At("HEAD"), cancellable = true)
    public void dsurround$getMaxDelay(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.musicOptions.reduceWaitTime != 0) {
            var keepAmount = 100 - MixinHelpers.musicOptions.reduceWaitTime;
            var newWaitTime = Mth.clamp((this.maxDelay * keepAmount) / 100, 1, this.maxDelay);
            cir.setReturnValue(newWaitTime);
        }
    }
}
