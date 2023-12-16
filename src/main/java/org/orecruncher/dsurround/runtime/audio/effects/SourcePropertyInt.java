package org.orecruncher.dsurround.runtime.audio.effects;

import net.minecraft.util.math.MathHelper;
import org.lwjgl.openal.AL11;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;

public final class SourcePropertyInt {

    private final int property;
    private final int min;
    private final int max;
    private int value;
    private boolean process;

    public SourcePropertyInt(final int property, final int val, final int min, final int max) {
        this.property = property;
        this.min = min;
        this.max = max;
        this.value = val;
        this.process = false;
    }

    public boolean doProcess() {
        return this.process;
    }

    public void setProcess(final boolean flag) {
        this.process = flag;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(final int f) {
        this.value = MathHelper.clamp(f, this.min, this.max);
    }

    public void apply(final int sourceId) {
        if (doProcess()) {
            AL11.alSourcei(sourceId, this.property, getValue());
            AudioUtilities.validate("SourcePropertyInt apply");
        }
    }
}