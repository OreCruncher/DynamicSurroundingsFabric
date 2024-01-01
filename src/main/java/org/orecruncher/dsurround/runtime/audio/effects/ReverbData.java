package org.orecruncher.dsurround.runtime.audio.effects;

import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

public final class ReverbData extends EffectData {

    // Defaults based on spec
    public float density = EXTEfx.AL_EAXREVERB_DEFAULT_DENSITY;
    public float diffusion = EXTEfx.AL_EAXREVERB_DEFAULT_DIFFUSION;
    public float gain = EXTEfx.AL_EAXREVERB_DEFAULT_GAIN;
    public float gainHF = EXTEfx.AL_EAXREVERB_DEFAULT_GAINHF;
    public float decayTime = EXTEfx.AL_EAXREVERB_DEFAULT_DECAY_TIME;
    public float decayHFRatio = EXTEfx.AL_EAXREVERB_DEFAULT_DECAY_HFRATIO;
    public float reflectionsGain = EXTEfx.AL_EAXREVERB_DEFAULT_REFLECTIONS_GAIN;
    public float reflectionsDelay = EXTEfx.AL_EAXREVERB_DEFAULT_REFLECTIONS_DELAY;
    public float lateReverbGain = EXTEfx.AL_EAXREVERB_DEFAULT_LATE_REVERB_GAIN;
    public float lateReverbDelay = EXTEfx.AL_EAXREVERB_DEFAULT_LATE_REVERB_DELAY;
    public float airAbsorptionGainHF = EXTEfx.AL_EAXREVERB_DEFAULT_AIR_ABSORPTION_GAINHF;
    public float roomRolloffFactor = EXTEfx.AL_EAXREVERB_DEFAULT_ROOM_ROLLOFF_FACTOR;
    public int decayHFLimit = AL10.AL_TRUE;

    public ReverbData() {

    }

    @Override
    public void clamp() {
        this.density = Mth.clamp(this.density, EXTEfx.AL_EAXREVERB_MIN_DENSITY, EXTEfx.AL_EAXREVERB_MAX_DENSITY);
        this.diffusion = Mth.clamp(this.diffusion, EXTEfx.AL_EAXREVERB_MIN_DIFFUSION, EXTEfx.AL_EAXREVERB_MAX_DIFFUSION);
        this.gain = Mth.clamp(this.gain, EXTEfx.AL_EAXREVERB_MIN_GAIN, EXTEfx.AL_EAXREVERB_MAX_GAIN);
        this.gainHF = Mth.clamp(this.gainHF, EXTEfx.AL_EAXREVERB_MIN_GAINHF, EXTEfx.AL_EAXREVERB_MAX_GAINHF);
        this.decayTime = Mth.clamp(this.decayTime, EXTEfx.AL_EAXREVERB_MIN_DECAY_TIME, EXTEfx.AL_EAXREVERB_MAX_DECAY_TIME);
        this.decayHFRatio = Mth.clamp(this.decayHFRatio, EXTEfx.AL_EAXREVERB_MIN_DECAY_HFRATIO, EXTEfx.AL_EAXREVERB_MAX_DECAY_HFRATIO);
        this.reflectionsGain = Mth.clamp(this.reflectionsGain, EXTEfx.AL_EAXREVERB_MIN_REFLECTIONS_GAIN, EXTEfx.AL_EAXREVERB_MAX_REFLECTIONS_GAIN);
        this.reflectionsDelay = Mth.clamp(this.reflectionsDelay, EXTEfx.AL_EAXREVERB_MIN_REFLECTIONS_DELAY, EXTEfx.AL_EAXREVERB_MAX_REFLECTIONS_DELAY);
        this.lateReverbGain = Mth.clamp(this.lateReverbGain, EXTEfx.AL_EAXREVERB_MIN_LATE_REVERB_GAIN, EXTEfx.AL_EAXREVERB_MAX_LATE_REVERB_GAIN);
        this.lateReverbDelay = Mth.clamp(this.reflectionsDelay, EXTEfx.AL_EAXREVERB_MIN_LATE_REVERB_DELAY, EXTEfx.AL_EAXREVERB_MAX_LATE_REVERB_DELAY);
        this.airAbsorptionGainHF = Mth.clamp(this.airAbsorptionGainHF, EXTEfx.AL_EAXREVERB_MIN_AIR_ABSORPTION_GAINHF, EXTEfx.AL_EAXREVERB_MAX_AIR_ABSORPTION_GAINHF);
        this.roomRolloffFactor = Mth.clamp(this.roomRolloffFactor, EXTEfx.AL_EAXREVERB_MIN_ROOM_ROLLOFF_FACTOR, EXTEfx.AL_EAXREVERB_MAX_ROOM_ROLLOFF_FACTOR);
        this.decayHFLimit = Mth.clamp(this.decayHFLimit, AL10.AL_FALSE, AL10.AL_TRUE);
    }
}