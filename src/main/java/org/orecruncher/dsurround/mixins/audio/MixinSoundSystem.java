package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundSystem.class)
public class MixinSoundSystem {

    @Final
    @Shadow
    private SoundEngine soundEngine;

    @Inject(method = "start()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundEngine;init(Ljava/lang/String;Z)V", shift = At.Shift.AFTER))
    public void dsurround_init(CallbackInfo ci) {
        AudioUtilities.initialize(this.soundEngine);
    }

    @Inject(method = "stop()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundLoader;close()V", shift = At.Shift.BEFORE))
    public void dsurround_deinit(CallbackInfo ci) {
        AudioUtilities.deinitialize(this.soundEngine);
    }

    /**
     * Callback will trigger creation of sound context information for the sound play once it has been queued to the
     * sound engine.  It will also perform the first calculations of sound effects based on the player environment.
     */
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    public void dsurround_onSoundPlay(SoundInstance sound, CallbackInfo ci, WeightedSoundSet weightedSoundSet, Identifier identifier, Sound sound2, float f, float g, SoundCategory soundCategory, float h, float i, SoundInstance.AttenuationType attenuationType, boolean bl, Vec3d vec3d, boolean bl3, boolean bl4, CompletableFuture<Channel.SourceManager> completableFuture, Channel.SourceManager sourceManager) {
        try {
            SoundFXProcessor.onSoundPlay(sound, sourceManager);
            AudioUtilities.onSoundPlay(sound);
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in dsurround_onSoundPlay()!");
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void dsurround_play(SoundInstance sound, CallbackInfo ci) {
        try {
            if (SoundInstanceHandler.shouldBlockSoundPlay(sound))
                ci.cancel();
        } catch (final Exception ignore) {
        }
    }

    /**
     * @author
     * @reason Replacing algorithm for adjusting volume using other external factors
     */
    @Overwrite()
    private float getAdjustedVolume(SoundInstance sound) {
        return SoundVolumeEvaluator.getAdjustedVolume(sound);
    }

    @Redirect(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"))
    private float dsurround_playGetAdjustedVolume(SoundSystem instance, float volume, SoundCategory category, SoundInstance sound) {
        return SoundVolumeEvaluator.getAdjustedVolume(sound);
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;<init>(DDD)V"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private void dsurround_soundRangeCheck(SoundInstance sound, CallbackInfo ci, WeightedSoundSet weightedSoundSet, Identifier identifier, Sound sound2, float f, float g, SoundCategory soundCategory, float h, float i, SoundInstance.AttenuationType attenuationType, boolean isGlobal) {
        if (Client.Config.soundSystem.enableSoundPruning) {
            // If not in range of the listener cancel.
            if (!SoundInstanceHandler.inRange(AudioUtilities.getSoundListener().getTransform().position(), sound, 4)) {
                Client.LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> "TOO FAR: " + AudioUtilities.debugString(sound));
                ci.cancel();
            }
        }
    }
}
