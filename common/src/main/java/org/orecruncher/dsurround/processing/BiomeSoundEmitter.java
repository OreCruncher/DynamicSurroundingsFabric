package org.orecruncher.dsurround.processing;

import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.BackgroundSoundLoop;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

public final class BiomeSoundEmitter {

    private final IModLog logger;
    private final IAudioPlayer audioPlayer;
    private final ISoundFactory soundEvent;
    private final BackgroundSoundLoop acousticSource;

    private boolean done = false;

    public BiomeSoundEmitter(final IModLog logger, final IAudioPlayer audioPlayer, final ISoundFactory event) {
        this.logger = logger;
        this.audioPlayer = audioPlayer;
        this.soundEvent = event;
        this.acousticSource = event.createBackgroundSoundLoop();
    }

    public void tick() {

        boolean isPlaying = this.audioPlayer.isPlaying(this.acousticSource);

        // If the current sound is playing and the sound is fading, terminate the sound.
        if (isPlaying) {
            return;
        } else if (isFading()) {
            // If we get here, the sound is no longer playing and is in the
            // fading state. This is possible because the actual sound
            // volume down in the engine could have hit 0 but the tick
            // handler on the sound did not have a chance to get there
            // first.
            this.done = true;
            return;
        }

        // Play the sound if needed
        this.audioPlayer.play(this.acousticSource);
    }

    public void setVolumeScale(final float scale) {
        this.acousticSource.setScaleTarget(scale);
    }

    public void fadeOut() {
        this.logger.debug("FADE OUT: %s", this.acousticSource.toString());
        this.acousticSource.fadeOut();
    }

    public boolean isFading() {
        return this.acousticSource.isFading();
    }

    public void fadeIn() {
        this.logger.debug("FADE IN: %s", this.acousticSource.toString());
        this.acousticSource.fadeIn();
    }

    public boolean isDone() {
        return this.done || this.acousticSource.isStopped();
    }

    public void stop() {
        this.audioPlayer.stop(this.acousticSource);
        this.done = true;
    }

    public ISoundFactory getSoundEvent() {
        return this.soundEvent;
    }

    @Override
    public String toString() {
        return this.acousticSource.toString();
    }

}