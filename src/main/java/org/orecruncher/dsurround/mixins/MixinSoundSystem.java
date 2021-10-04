package org.orecruncher.dsurround.mixins;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void play(SoundInstance sound, CallbackInfo ci) {
        try {
            if (SoundInstanceHandler.shouldBlockSoundPlay(sound))
                ci.cancel();
        } catch (final Exception ignore) {
        }
    }

    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void getAdjustedVolume(SoundInstance sound, CallbackInfoReturnable<Float> ci) {
        try {
            ci.setReturnValue(SoundVolumeEvaluator.getAdjustedVolume(sound));
        } catch (final Exception ignore) {
        }
    }
}