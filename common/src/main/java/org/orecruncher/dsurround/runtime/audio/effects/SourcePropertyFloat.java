package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL11;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

/**
 * A float property of an OpenAL source (e.g. air absorption), clamped to a range. Only uploaded while enabled, and
 * only when it changed since the last upload; a new instance counts as changed.
 */
public final class SourcePropertyFloat {

    private final int property;
    private final float min;
    private final float max;
    private float value;
    private boolean process;
    private boolean changed = true;

    public SourcePropertyFloat(final int property, final float val, final float min, final float max) {
        this.property = property;
        this.value = val;
        this.min = min;
        this.max = max;
    }

    public boolean doProcess() {
        return this.process;
    }

    public void setProcess(final boolean flag) {
        if (this.process != flag) {
            this.process = flag;
            this.changed = true;
        }
    }

    public float getValue() {
        return this.value;
    }

    public void setValue(final float f) {
        float clamped = f <= this.min ? this.min : Math.min(f, this.max);
        if (clamped != this.value) {
            this.value = clamped;
            this.changed = true;
        }
    }

    /**
     * Uploads the value if enabled and changed since the last upload.
     */
    public void apply(final int sourceId) {
        if (!this.takeChanged() || !this.process)
            return;
        AL11.alSourcef(sourceId, this.property, this.value);
        AudioUtilities.validate("SourcePropertyFloat apply");
    }

    /**
     * Whether it changed since this was last called.
     */
    boolean takeChanged() {
        boolean result = this.changed;
        this.changed = false;
        return result;
    }
}
