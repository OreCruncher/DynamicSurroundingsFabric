package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    @Final
    @Shadow
    private Library library;

    @Inject(method = "loadLibrary()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;init(Ljava/lang/String;Z)V", shift = At.Shift.AFTER))
    public void dsurround_init(CallbackInfo ci) {
        AudioUtilities.initialize(this.library);
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;cleanup()V", shift = At.Shift.BEFORE))
    public void dsurround_deinit(CallbackInfo ci) {
        AudioUtilities.deinitialize(this.library);
    }

    /**
     * Callback will trigger the creation of sound context information for the sound play once it has been queued to the
     * sound engine.  It will also perform the first calculations of sound effects based on the player environment.
     */
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    public void dsurround_onSoundPlay(SoundInstance soundInstance, CallbackInfo ci, WeighedSoundEvents weighedSoundEvents, ResourceLocation resourceLocation, Sound sound, float f, float g, SoundSource soundSource, float h, float i, SoundInstance.Attenuation attenuation, boolean bl, Vec3 vec3, boolean bl2, boolean bl3, CompletableFuture<?> completableFuture, ChannelAccess.ChannelHandle channelHandle) {
        try {
            SoundFXProcessor.onSoundPlay(soundInstance, channelHandle);
            AudioUtilities.onSoundPlay(soundInstance);
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSoundPlay()!");
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void dsurround_play(SoundInstance sound, CallbackInfo ci) {
        try {
            if (SoundInstanceHandler.shouldBlockSoundPlay(sound))
                ci.cancel();
        } catch (final Exception t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_play()!");
        }
    }

    /**
     * @author OreCruncher
     * @reason Replacing algorithm for adjusting volume using other external factors
     */
    @Overwrite()
    private float calculateVolume(SoundInstance sound) {
        return SoundVolumeEvaluator.getAdjustedVolume(sound);
    }

    @Redirect(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float dsurround_playGetAdjustedVolume(SoundEngine instance, float f, SoundSource soundSource, SoundInstance sound) {
        return SoundVolumeEvaluator.getAdjustedVolume(sound);
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private void dsurround_soundRangeCheck(SoundInstance soundInstance, CallbackInfo ci, WeighedSoundEvents weighedSoundEvents, ResourceLocation resourceLocation, Sound sound, float f, float g, SoundSource soundSource, float h, float i, SoundInstance.Attenuation attenuation, boolean bl) {
        if (MixinHelpers.soundSystemConfig.enableSoundPruning) {
            // If not in range of the listener, cancel.
            if (!SoundInstanceHandler.inRange(AudioUtilities.getSoundListener().getTransform().position(), soundInstance, 4)) {
                MixinHelpers.LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> "TOO FAR: " + AudioUtilities.debugString(soundInstance));
                ci.cancel();
            }
        }
    }
}
