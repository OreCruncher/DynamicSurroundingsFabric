package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundSystem.class)
public class MixinSoundSystem {

    @Final
    @Shadow
    private SoundEngine soundEngine;

    @Inject(method = "start()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundEngine;init()V", shift = At.Shift.AFTER))
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
    public void dsurround_onSoundPlay(SoundInstance sound, CallbackInfo ci, WeightedSoundSet weightedSoundSet, Identifier identifier, Sound sound2, float f, float g, SoundCategory soundCategory, float h, float i, SoundInstance.AttenuationType attenuationType, boolean bl, Vec3d vec3d, boolean bl3, boolean bl4, CompletableFuture completableFuture, Channel.SourceManager sourceManager) {
        try {
            SoundFXProcessor.onSoundPlay(sound, sourceManager);
            AudioUtilities.onPlaySound(sound);
        } catch(final Throwable t) {
            Client.LOGGER.error(t, "Error in dsurround_onSoundPlay()!");
        }
    }

}
