package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL11;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

/**
 * A float property of an OpenAL source (e.g. air absorption), clamped to a range. Only uploaded while enabled, and
 * only when it changed since the last upload; a new instance counts as changed. Disabled after a value was uploaded,
 * the source gets the property's default back.
 */
public final class SourcePropertyFloat {

    private final int property;
    private final float defaultValue;
    private final float min;
    private final float max;
    private float value;
    private boolean process;
    private boolean changed = true;
    // Whether the source has a value of ours rather than its default
    private boolean uploaded;

    /**
     * @param val the property's default, which a source has until a value is uploaded
     */
    public SourcePropertyFloat(final int property, final float val, final float min, final float max) {
        this.property = property;
        this.defaultValue = val;
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
     * Uploads the value if enabled and changed since the last upload, or the default if disabled since.
     */
    public void apply(final int sourceId) {
        var upload = this.takeUpload();
        if (upload == null)
            return;
        AL11.alSourcef(sourceId, this.property, upload);
        AudioUtilities.validate("SourcePropertyFloat apply");
    }

    /**
     * What to upload now, if anything: the value if enabled and changed since the last upload, the default if
     * disabled since a value was uploaded.
     */
    Float takeUpload() {
        if (!this.takeChanged())
            return null;
        if (this.process) {
            this.uploaded = true;
            return this.value;
        }
        if (this.uploaded) {
            this.uploaded = false;
            return this.defaultValue;
        }
        return null;
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
