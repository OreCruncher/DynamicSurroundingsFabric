package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.gui.sound.SoundToast;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.mixinutils.IMusicManager;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MixinMusicManager implements IMusicManager {
    @Shadow
    @Nullable
    private SoundInstance currentMusic;
    @Shadow
    private int nextSongDelay;

    @Unique
    private boolean dsurround_pauseTicking;

    @Override
    public String dsurround_getDiagnosticText() {
        String playingSound = "Nothing playing";
        if (this.currentMusic != null)
            playingSound = this.currentMusic.getLocation().toString();
        var result = "Music Manager: %d (%s)".formatted(this.nextSongDelay, playingSound);
        if (this.dsurround_pauseTicking)
            result += " (PAUSED)";
        return result;
    }

    @Override
    public void dsurround_doCommand(String command) {
        if ("reset".equals(command)) {
            MusicManager self = (MusicManager) (Object) this;
            self.stopPlaying();
            this.nextSongDelay = 100;
            this.dsurround_pauseTicking = false;
        } else if ("pause".equals(command)) {
            this.dsurround_setPaused(true);
        } else if ("unpause".equals(command)) {
            this.dsurround_setPaused(false);
        }
    }

    @Override
    public void dsurround_setPaused(boolean flag) {
        var self = (MusicManager) (Object) this;
        if (flag) {
            MixinHelpers.LOGGER.info("Stopping MusicManager");
            this.dsurround_pauseTicking = true;
            self.stopPlaying();
        } else {
            MixinHelpers.LOGGER.info("Starting MusicManager");
            this.nextSongDelay = 100;
            this.dsurround_pauseTicking = false;
        }
    }

    @Override
    public Component dsurround_whatsPlaying() {
        if (this.currentMusic == null)
            return Component.translatable("dsurround.text.musicmanager.nothing");

        // Lookup meta information
        var metaData = MixinHelpers.SOUND_LIBRARY.getSoundMetadata(this.currentMusic.getLocation());
        if (metaData == null || Component.empty().equals(metaData.getTitle()))
            return Component.literal(this.currentMusic.getLocation().toString());

        var title = metaData.getTitle().copy().withColor(ColorPalette.PUMPKIN_ORANGE.getValue());
        var author = metaData.getCredits().get(0).author().copy().withColor(ColorPalette.WHEAT.getValue());
        return Component.translatable("dsurround.text.musicmanager.playing", title, author, Component.translationArg(this.currentMusic.getLocation()));
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    public void dsurround_pauseTickCheck(CallbackInfo ci) {
        if (this.dsurround_pauseTicking)
            ci.cancel();
    }

    @Inject(method = "startPlaying(Lnet/minecraft/sounds/Music;)V", at = @At("RETURN"))
    public void dsurround_startPlaying(Music music, CallbackInfo ci) {
        if (MixinHelpers.soundOptions.displayToastMessagesForMusic)
            SoundToast.create(music);
    }
}
