package org.orecruncher.dsurround.mixins.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Final
    @Shadow
    private Library library;

    // A bit of hackery to work around the fact that NeoForge does not like mixin Redirect. When a sound is played
    // the reference is cached so that later on in processing it can be used to calculate the sound volume based
    // on configuration settings. It's a bit brittle but should work.
    @Unique
    private SoundInstance dsurround$currentSoundInstance;

    @Inject(method = "loadLibrary()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;init(Ljava/lang/String;Z)V", shift = At.Shift.AFTER))
    public void dsurround$init(CallbackInfo ci) {
        AudioUtilities.initialize(this.library);
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;cleanup()V", shift = At.Shift.BEFORE))
    public void dsurround$deinit(CallbackInfo ci) {
        AudioUtilities.deinitialize(this.library);
    }

    /**
     * Callback will trigger the creation of sound context information for the sound play once it has been queued to the
     * sound engine.  It will also perform the first calculations of sound effects based on the player environment.
     */
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    public void dsurround$onSoundPlay(SoundInstance soundInstance, CallbackInfo ci, WeighedSoundEvents weighedSoundEvents, ResourceLocation resourceLocation, Sound sound, float f, float g, SoundSource soundSource, float h, float i, SoundInstance.Attenuation attenuation, boolean bl, Vec3 vec3, boolean bl2, boolean bl3, CompletableFuture<?> completableFuture, ChannelAccess.ChannelHandle channelHandle) {
        try {
            SoundFXProcessor.onSoundPlay(soundInstance, channelHandle);
            AudioUtilities.onSoundPlay(soundInstance);
        } catch(final Throwable t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_onSoundPlay()!");
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void dsurround$play(SoundInstance sound, CallbackInfo ci) {
        try {
            // Ensure the sound is being played on the client thread. If it isn't cancel
            // the play and emit a message indicating such. This should also protect
            // dsurround$currentSoundInstance.
            if (!GameUtils.getMC().isSameThread()) {
                MixinHelpers.LOGGER.warn("Attempt to play sound (%s) from non-client thread; discarding", sound.getLocation());
                ci.cancel();
            }
            // Check to see if the sound is blocked or being culled
            else if (SoundInstanceHandler.shouldBlockSoundPlay(sound))
                ci.cancel();
            // Attempt a remapping if configured to do so
            else if (SoundInstanceHandler.remapSoundPlay(sound))
                ci.cancel();
            // Looks like it is going to play
            else
                this.dsurround$currentSoundInstance = sound;
        } catch (final Exception t) {
            MixinHelpers.LOGGER.error(t, "Error in dsurround_play()!");
        }
    }

    /**
     * Update the volume based on current settings and environment. Note the documentation above for the
     * dsurround$currentSoundInstance field.
     */
    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"), remap = false)
    private float dsurround$calculateVolume(SoundEngine soundEngine, float f, SoundSource soundsource, Operation<Float> original) {
        try {
            if (this.dsurround$currentSoundInstance != null) {
                var instance = this.dsurround$currentSoundInstance;
                this.dsurround$currentSoundInstance = null;
                return SoundVolumeEvaluator.getAdjustedVolume(instance, soundsource);
            }
        } catch (Throwable ex) {
            // Something went wrong. Since the call was not canceled, it will continue with the existing implementation.
            MixinHelpers.LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, "Error calculating sound volume: %s", ex);
        }
        return original.call(soundEngine, f, soundsource);
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"), cancellable = true)
    private void dsurround$soundRangeCheck(SoundInstance soundInstance, CallbackInfo ci) {
        if (MixinHelpers.soundSystemConfig.enableSoundPruning) {
            // If not in range of the listener, cancel.
            if (!SoundInstanceHandler.inRange(AudioUtilities.getSoundListener().getTransform().position(), soundInstance, 4)) {
                MixinHelpers.LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> "TOO FAR: " + AudioUtilities.debugString(soundInstance));
                ci.cancel();
            }
        }
    }
}
