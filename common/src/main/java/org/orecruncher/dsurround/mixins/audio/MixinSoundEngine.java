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
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.sound.SoundVolumeEvaluator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Unique
    private static final boolean dsurround$compatibleSoundDiscardEnvironment = !FeatureConflict.OFF_THREAD_SOUND_DISCARD.isBlocked();

    @Final
    @Shadow
    public Library library;

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
     * always the one being played.
     */
    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"), remap = false)
    private float dsurround$calculateVolume(SoundEngine soundEngine, float f, SoundSource soundsource, Operation<Float> original, @Local(argsOnly = true) SoundInstance sound) {
        try {
            return SoundVolumeEvaluator.getAdjustedVolume(sound, soundsource);
        } catch (Throwable t) {
            // Something went wrong: the game's own volume is used
            MixinHelpers.SOUND_VOLUME_GUARD.failed(t);
            return original.call(soundEngine, f, soundsource);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"), cancellable = true)
    private void dsurround$soundRangeCheck(SoundInstance soundInstance, CallbackInfo ci) {
        try {
            if (MixinHelpers.soundSystemConfig.enableSoundPruning) {
                // If not in range of the listener, cancel.
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
