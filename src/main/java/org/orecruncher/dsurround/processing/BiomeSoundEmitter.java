package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvent;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.BackgroundSoundLoop;
import org.orecruncher.dsurround.sound.SoundFactory;

@Environment(EnvType.CLIENT)
public final class BiomeSoundEmitter {

    private final SoundManager manager;
    private final SoundEvent soundEvent;
    private final BackgroundSoundLoop acousticSource;

    private boolean done = false;

    public BiomeSoundEmitter(final SoundEvent event) {
        this.soundEvent = event;
        this.acousticSource = SoundFactory.createBackgroundSoundLoop(event);
        this.manager = GameUtils.getSoundHander();
    }

    public void tick() {

        boolean isPlaying = this.manager.isPlaying(this.acousticSource);

        // If the current sound is playing and the sound is fading just terminate the sound.
        if (isPlaying) {
            return;
        } else if (isFading()) {
            // If we get here the sound is no longer playing and is in the
            // fading state. This is possible because the actual sound
            // volume down in the engine could have hit 0 but the tick
            // handler on the sound did not have a chance to get there
            // first.
            this.done = true;
            return;
        }

        // Play the sound if need be
        this.manager.play(this.acousticSource);
    }

    public void setVolumeScale(final float scale) {
        this.acousticSource.setScaleTarget(scale);
    }

    public void fade() {
        Client.LOGGER.debug("FADE: %s", this.acousticSource.toString());
        this.acousticSource.fadeOut();
    }

    public boolean isFading() {
        return this.acousticSource.isFading();
    }

    public void unfade() {
        Client.LOGGER.debug("UNFADE: %s", this.acousticSource.toString());
        this.acousticSource.fadeIn();
    }

    public boolean isDonePlaying() {
        return this.done || this.acousticSource.isDone();
    }

    public void stop() {
        this.manager.stop(this.acousticSource);
        this.done = true;
    }

    public SoundEvent getSoundEvent() {
        return this.soundEvent;
    }
    @Override
    public String toString() {
        return this.acousticSource.toString();
    }

}