package org.orecruncher.dsurround.runtime.audio.effects;

import net.minecraft.util.Mth;
import org.lwjgl.openal.AL11;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public final class SourcePropertyFloat {

    private final int property;
    private final float min;
    private final float max;
    private float value;
    private boolean process;

    public SourcePropertyFloat(final int property, final float val, final float min, final float max) {
        this.property = property;
        this.value = val;
        this.min = min;
        this.max = max;
        this.process = false;
    }

    public boolean doProcess() {
        return this.process;
    }

    public void setProcess(final boolean flag) {
        this.process = flag;
    }

    public float getValue() {
        return this.value;
    }

    public void setValue(final float f) {
        this.value = Mth.clamp(f, this.min, this.max);
    }

    public void apply(final int sourceId) {
        if (doProcess()) {
            AL11.alSourcef(sourceId, this.property, getValue());
            AudioUtilities.validate("SourcePropertyFloat apply");
        }
    }
}