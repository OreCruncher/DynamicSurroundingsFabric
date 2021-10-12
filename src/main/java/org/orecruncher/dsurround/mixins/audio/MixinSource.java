package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.Source;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.orecruncher.dsurround.runtime.audio.SourceContext;
import org.orecruncher.dsurround.xface.ISourceContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Source.class)
public class MixinSource implements ISourceContext {

    private SourceContext dsurround_data = null;

    @Shadow
    @Final
    private int pointer;

    @Override
    public int getId() {
        return this.pointer;
    }

    @Nullable
    @Override
    public SourceContext getData() {
        return this.dsurround_data;
    }

    @Override
    public void setData(@Nullable SourceContext data) {
        this.dsurround_data = data;
    }

    /**
     * Called when the sound is ticked by the sound engine. This will set the sound effect properties for the sound
     * at the time of play.
     * @param ci Ignored
     */
    @Inject(method = "play()V", at = @At("HEAD"))
    public void dsurround_onPlay(CallbackInfo ci) {
        try {
            SoundFXProcessor.tick((Source) ((Object) this));
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in onPlay()!");
        }
    }

    /**
     * Called when the sound is ticked by the sound engine. This will set the sound effect properties for the sound
     * at the time of tick.
     * @param ci Ignored
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    public void dsurround_onTick(CallbackInfo ci) {
        try {
            SoundFXProcessor.tick((Source) ((Object) this));
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in onTick()!");
        }
    }

    /**
     * Called when a sounds stops playing.  Any context information sndctrl has generated will be cleaned up.
     * @param ci Ignored
     */
    @Inject(method = "stop()V", at = @At("HEAD"))
    public void dsurround_onStop(CallbackInfo ci) {
        try {
            SoundFXProcessor.stopSoundPlay((Source) ((Object) this));
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in onStop()!");
        }
    }

    /**
     * Called after the audio stream buffer has been generated by the sound engine.  If the sound has non-linear
     * attenuation and is not mono, it will be converted to mono format.  Non-mono sounds will be played in the sound engine
     * as if they are non-linear because it cannot convert non-mono sounds for 3D environmental play.
     * @param audioStream Buffer to convert to mono if needed.
     * @param ci Call will always be cancelled.
     */
    @ModifyVariable(method = "setStream(Lnet/minecraft/client/sound/AudioStream;)V", at = @At("HEAD"))
    public AudioStream dsurround_monoConversion(AudioStream audioStream, CallbackInfo ci) {
        try {
            var src = (Source) ((Object) this);
            return SoundFXProcessor.doMonoConversion(src, audioStream);
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in onPlayBuffer()!");
        }
        return audioStream;
    }
}