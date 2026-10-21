package org.orecruncher.dsurround.mixins.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.lib.text.StringUtils;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.AudioDeviceRetry;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundRange;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Unique
    private static final boolean dsurround$compatibleSoundDiscardEnvironment = !FeatureConflict.OFF_THREAD_SOUND_DISCARD.isBlocked();

    @Final
    @Shadow
    public Library library;

    @Unique
    private static final AudioDeviceRetry dsurround$deviceRetry = new AudioDeviceRetry(() -> MixinHelpers.soundSystemConfig.throttleDeviceRetries, System::nanoTime);

    @Inject(method = "loadLibrary()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;init(Ljava/lang/String;Z)V", shift = At.Shift.AFTER))
    public void dsurround$init(CallbackInfo ci) {
        dsurround$deviceRetry.started();
        AudioUtilities.initialize(this.library);
    }

    /**
     * Once a restart after losing the device has failed, the closed device still reads as lost every tick: restart
     * only once a while until one starts (see {@link AudioDeviceRetry})
     */
    @WrapOperation(method = "shouldChangeDevice()Z", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;isCurrentDeviceDisconnected()Z"))
    private boolean dsurround$deviceLost(Library library, Operation<Boolean> original) {
        return dsurround$deviceRetry.shouldRestart(original.call(library));
    }

    /**
     * "Audio device was lost!", said once rather than at each retry
     */
    @WrapOperation(method = "shouldChangeDevice()Z", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", remap = false))
    private void dsurround$deviceLostLog(Logger logger, String message, Operation<Void> original) {
        if (dsurround$deviceRetry.shouldLogRestart())
            original.call(logger, message);
    }

    /**
     * The sound events without sounds, listed at every restart, not again at each retry
     */
    @WrapOperation(method = "reload()V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V", remap = false))
    private void dsurround$missingSoundLog(Logger logger, String message, Object event, Operation<Void> original) {
        if (dsurround$deviceRetry.shouldLogRestart())
            original.call(logger, message, event);
    }

    /**
     * The failure to start, with its stack trace, logged once while the engine stays down
     */
    @WrapOperation(method = "loadLibrary()V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Throwable;)V", remap = false))
    private void dsurround$startFailedLog(Logger logger, Marker marker, String message, Throwable error, Operation<Void> original) {
        if (dsurround$deviceRetry.startFailed()) {
            original.call(logger, marker, message, error);
            if (dsurround$deviceRetry.isEnabled())
                MixinHelpers.LOGGER.warn("Sound is off until an audio device can be opened; trying again every few seconds, without logging each try");
        }
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;cleanup()V", shift = At.Shift.BEFORE))
    public void dsurround$deinit(CallbackInfo ci) {
        AudioUtilities.deinitialize(this.library);
    }

    /**
     * Callback will trigger the creation of sound context information for the sound play once it has been queued to the
     * sound engine.  It will also perform the first calculations of sound effects based on the player environment.
     * <p>
     * Only the channel handle is taken from play()'s locals, found by its type (there is one), so a change to play()'s
     * other locals, by a Minecraft update, a NeoForge patch or another mod, doesn't stop this applying.
     */
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    public void dsurround$onSoundPlay(SoundInstance soundInstance, CallbackInfo ci, @Local ChannelAccess.ChannelHandle channelHandle) {
        try {
            EnhancedSoundProcessor.onSoundPlay(soundInstance, channelHandle);
            AudioUtilities.onSoundPlay(soundInstance);
        } catch(final Throwable t) {
            MixinHelpers.SOUND_START_GUARD.failed(t);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void dsurround$play(SoundInstance sound, CallbackInfo ci) {
        try {
            // Ensure the sound is being played on the client thread. If it isn't cancel
            // the play and emit a message indicating such.
            if (dsurround$compatibleSoundDiscardEnvironment && MixinHelpers.soundOptions.discardNonClientSoundPlays && !GameUtils.getMC().isSameThread()) {
                MixinHelpers.LOGGER.warn("Attempt to play sound (%s) from non-client thread; discarding".formatted(sound.getLocation()));
                if (MixinHelpers.soundOptions.logStacktraceWhenDiscarding) {
                    MixinHelpers.LOGGER.warn(StringUtils.generateStackTrace(Thread.currentThread().getStackTrace()));
                }
                ci.cancel();
            }
            // Check to see if the sound is blocked or being culled
            else if (SoundInstanceHandler.shouldBlockSoundPlay(sound))
                ci.cancel();
            // Attempt a remapping if configured to do so
            else if (SoundInstanceHandler.remapSoundPlay(sound))
                ci.cancel();
        } catch (final Throwable t) {
            MixinHelpers.SOUND_FILTER_GUARD.failed(t);
        }
    }

    /**
     * Update the volume based on current settings and environment. The sound is play()'s own parameter, so it is
     * always the one being played. The volume play() read is used, not read again: reading draws a new random sample,
     * and play() works out the sound's range from the one it read.
     */
    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"), remap = false)
    private float dsurround$calculateVolume(SoundEngine soundEngine, float f, SoundSource soundsource, Operation<Float> original, @Local(argsOnly = true) SoundInstance sound) {
        try {
            return SoundVolumeEvaluator.getAdjustedVolume(sound, f, soundsource);
        } catch (Throwable t) {
            // Something went wrong: the game's own volume is used
            MixinHelpers.SOUND_VOLUME_GUARD.failed(t);
            return original.call(soundEngine, f, soundsource);
        }
    }

    /**
     * The volume of a sound already playing: set every tick for a ticking sound (the mod's own loops are), and for
     * every playing sound when a volume slider moves. Without this they would drop the configured scale after their
     * first tick.
     */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void dsurround$calculatePlayingVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        try {
            cir.setReturnValue(SoundVolumeEvaluator.getAdjustedVolume(sound, null));
        } catch (Throwable t) {
            // Something went wrong: the game's own volume is used
            MixinHelpers.SOUND_VOLUME_GUARD.failed(t);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"), cancellable = true)
    private void dsurround$soundRangeCheck(SoundInstance soundInstance, CallbackInfo ci) {
        try {
            // A sound out of range plays silently in the game, so one that is over before it could be heard is
            // dropped; one that lasts is kept, as the listener or the sound may come closer
            if (MixinHelpers.soundSystemConfig.enableSoundPruning && !SoundRange.lasts(soundInstance)) {
                if (!SoundInstanceHandler.inRange(AudioUtilities.getSoundListener().getTransform().position(), soundInstance, 4)) {
                    MixinHelpers.LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> "TOO FAR: " + AudioUtilities.debugString(soundInstance));
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            // The sound plays as the game would have it
            MixinHelpers.SOUND_RANGE_GUARD.failed(t);
        }
    }
}
