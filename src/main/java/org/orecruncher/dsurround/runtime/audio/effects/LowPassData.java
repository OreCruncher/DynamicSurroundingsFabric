package org.orecruncher.dsurround.runtime.audio.effects;

import net.minecraft.util.math.MathHelper;
import org.lwjgl.openal.EXTEfx;

public final class LowPassData extends EffectData {

    public float gain = EXTEfx.AL_LOWPASS_DEFAULT_GAIN;
    public float gainHF = EXTEfx.AL_LOWPASS_DEFAULT_GAINHF;

    public LowPassData() {
    }

    /**
     * Ensures that the effect data is properly bounded.
     */
    @Override
    public void clamp() {
        this.gain = MathHelper.clamp(this.gain, EXTEfx.AL_LOWPASS_MIN_GAIN, EXTEfx.AL_LOWPASS_MAX_GAIN);
        this.gainHF = MathHelper.clamp(this.gainHF, EXTEfx.AL_LOWPASS_MIN_GAINHF, EXTEfx.AL_LOWPASS_MAX_GAINHF);
    }
}