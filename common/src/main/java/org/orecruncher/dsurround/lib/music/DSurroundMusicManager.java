package org.orecruncher.dsurround.lib.music;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.gui.sound.SoundToast;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;

@Environment(EnvType.CLIENT)
public final class DSurroundMusicManager extends MusicManager {

    private static final IModLog LOGGER = ModLog.createChild(ContainerManager.resolve(IModLog.class), "MusicManager");

    private boolean pauseTicking;

    public DSurroundMusicManager(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void tick() {
        if (!this.pauseTicking) {
            super.tick();
        }
    }

    @Override
    public void startPlaying(@NotNull Music music) {
        if (MixinHelpers.soundOptions.displayToastMessagesForMusic)
            SoundToast.create(music);
        super.startPlaying(music);
    }

    @Override
    public void stopPlaying(@NotNull Music music) {
        super.stopPlaying(music);
    }

    @Override
    public void stopPlaying() {
        super.stopPlaying();
    }

    @Override
    public boolean isPlayingMusic(@NotNull Music music) {
        return super.isPlayingMusic(music);
    }

    public void doCommand(Commands command) {
        switch (command) {
            case PAUSE -> this.setPaused(true);
            case RESET -> {
                this.stopPlaying();
                this.nextSongDelay = 100;
                this.pauseTicking = false;
            }
            case UNPAUSE -> this.setPaused(false);
            default -> {}
        }
    }

    public void setPaused(boolean paused) {
        if (paused) {
            LOGGER.info("Pausing MusicManager");
            this.pauseTicking = true;
            this.stopPlaying();
        } else {
            LOGGER.info("Unpausing MusicManager");
            this.nextSongDelay = 100;
            this.pauseTicking = false;
        }
    }

    public Component whatsPlaying() {
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

    public String getDiagnosticText() {
        String playingSound = "Nothing playing";
        if (this.currentMusic != null)
            playingSound = this.currentMusic.getLocation().toString();
        var result = "Music Manager: %d (%s)".formatted(this.nextSongDelay, playingSound);
        if (this.pauseTicking)
            result += " (PAUSED)";
        return result;
    }

    public enum Commands {
        RESET,
        PAUSE,
        UNPAUSE;

        public String commandName() {
            return this.name().toLowerCase();
        }

        public static Commands of(String name) {
            return Commands.valueOf(name.toUpperCase());
        }
    }

}
