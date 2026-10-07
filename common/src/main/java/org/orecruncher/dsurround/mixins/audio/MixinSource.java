package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Channel;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class MixinSource {

    /**
     * Called when the sound is ticked by the sound engine. This will set the sound effect properties for the sound
     * at the time of play.
     * @param ci Ignored
     */
    @Inject(method = "play()V", at = @At("HEAD"))
    public void dsurround$onSourcePlay(CallbackInfo ci) {
        try {
            SoundFXProcessor.onSourcePlay((Channel) (Object) this);
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSourcePlay()!");
        }
    }

    /**
     * Called when the sound is ticked by the sound engine. This will set the sound effect properties for the sound
     * at the time of tick.
     * @param ci Ignored
     */
    @Inject(method = "updateStream()V", at = @At("HEAD"))
    public void dsurround$onSourceTick(CallbackInfo ci) {
        try {
            SoundFXProcessor.tick((Channel) (Object) this);
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSourceTick()!");
        }
    }

    /**
     * Called when a sounds stops playing.  Any context information generated will be cleaned up.
     * @param ci Ignored
     */
    @Inject(method = "stop()V", at = @At("HEAD"))
    public void dsurround$onSourceStop(CallbackInfo ci) {
        try {
            SoundFXProcessor.stopSoundPlay((Channel) (Object) this);
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSourceStop()!");
        }
    }
}
