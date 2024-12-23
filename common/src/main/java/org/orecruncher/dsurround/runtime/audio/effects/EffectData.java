package org.orecruncher.dsurround.runtime.audio.effects;

public abstract class EffectData {

    protected boolean process;

    protected EffectData() {
        this.process = false;
    }

    /**
     * Indicates if the data set should be applied as an effect
     *
     * @return true to apply data; false otherwise
     */
    public boolean doProcess() {
        return this.process;
    }

    /**
     * Sets whether the data should be applied to a sound source.
     *
     * @param flag true to indicate data should be applied; false otherwise
     */
    public void setProcess(final boolean flag) {
        this.process = flag;
    }

    /**
     * Ensures that the effect data is properly bounded.
     */
    public abstract void clamp();
}