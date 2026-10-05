package org.orecruncher.dsurround.runtime.audio.effects;

/**
 * One low-pass filter setting for a sound: its gain and high frequency gain, or disabled (no filter). Written by the
 * sound calculation, read when uploading to OpenAL; both hold the sound's lock.
 * <p>
 * Tracks whether it changed since the last upload, so unchanged settings aren't sent again. A new instance counts
 * as changed, so a new sound always gets its first upload.
 */
public final class LowPassData {

    private float gain = 1F;
    private float gainHF = 1F;
    private boolean enabled;
    private boolean changed = true;

    /**
     * Enables the filter with these settings, clamped to 0 to 1.
     */
    public void set(float gain, float gainHF) {
        gain = clamp(gain);
        gainHF = clamp(gainHF);
        if (!this.enabled || gain != this.gain || gainHF != this.gainHF) {
            this.gain = gain;
            this.gainHF = gainHF;
            this.enabled = true;
            this.changed = true;
        }
    }

    /**
     * Disables the filter: the sound plays without it.
     */
    public void disable() {
        if (this.enabled) {
            this.enabled = false;
            this.changed = true;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public float gain() {
        return this.gain;
    }

    public float gainHF() {
        return this.gainHF;
    }

    /**
     * Whether it changed since this was last called.
     */
    boolean takeChanged() {
        boolean result = this.changed;
        this.changed = false;
        return result;
    }

    private static float clamp(float value) {
        // OpenAL's low-pass gain range
        return value <= 0F ? 0F : Math.min(value, 1F);
    }
}
