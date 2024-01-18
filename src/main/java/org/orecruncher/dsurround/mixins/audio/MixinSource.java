package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Channel;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.orecruncher.dsurround.runtime.audio.SourceContext;
import org.orecruncher.dsurround.mixinutils.ISourceContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Channel.class)
public class MixinSource implements ISourceContext {

    @Unique
    private SourceContext dsurround_data = null;

    @Shadow
    @Final
    private int source;

    @Override
    public int dsurround_getId() {
        return this.source;
    }

    @Override
    public Optional<SourceContext> dsurround_getData() {
        return Optional.ofNullable(this.dsurround_data);
    }

    @Override
    public void dsurround_setData(@Nullable SourceContext data) {
        this.dsurround_data = data;
    }

    /**
     * Called when the sound is ticked by the sound engine. This will set the sound effect properties for the sound
     * at the time of play.
     * @param ci Ignored
     */
    @Inject(method = "play()V", at = @At("HEAD"))
    public void dsurround_onSourcePlay(CallbackInfo ci) {
        try {
            SoundFXProcessor.onSourcePlay((Channel) ((Object) this));
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
    public void dsurround_onSourceTick(CallbackInfo ci) {
        try {
            SoundFXProcessor.tick((Channel) ((Object) this));
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSourceTick()!");
        }
    }

    /**
     * Called when a sounds stops playing.  Any context information sndctrl has generated will be cleaned up.
     * @param ci Ignored
     */
    @Inject(method = "stop()V", at = @At("HEAD"))
    public void dsurround_onSourceStop(CallbackInfo ci) {
        try {
            SoundFXProcessor.stopSoundPlay((Channel) ((Object) this));
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSourceStop()!");
        }
    }

}
