package org.orecruncher.dsurround.runtime.audio.effects;


import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public class ReverbEffectSlot extends Slot {

    public ReverbEffectSlot() {
        super(EXTEfx::alGenEffects);
    }

    @Override
    protected void init0() {
        EXTEfx.alEffecti(getSlot(), EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
    }

    public void apply(final ReverbData data, final AuxSlot aux) {
        if (isInitialized()) {
            if (data.doProcess()) {
                data.clamp();
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_DENSITY, data.density), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT density");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_DIFFUSION, data.diffusion), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT diffusion");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_GAIN, data.gain), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT gain");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_GAINHF, data.gainHF), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT gainHF");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_DECAY_TIME, data.decayTime), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT decayTime");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, data.decayHFRatio), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT decayHFRatio");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, data.reflectionsGain), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT reflectionsGain");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, data.reflectionsDelay), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT reflectionsDelay");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, data.lateReverbGain), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT lateReverbGain");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, data.lateReverbDelay), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT lateReverbDelay");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, data.airAbsorptionGainHF), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT airAbsorptionGainHF");
                AudioUtilities.execute(() -> EXTEfx.alEffectf(getSlot(), EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, data.roomRolloffFactor), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT roomRolloffFactor");
                AudioUtilities.execute(() -> EXTEfx.alEffecti(getSlot(), EXTEfx.AL_EAXREVERB_DECAY_HFLIMIT, data.decayHFLimit), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT decayHFLimit");
                AudioUtilities.execute(() -> EXTEfx.alAuxiliaryEffectSloti(aux.getSlot(), EXTEfx.AL_EFFECTSLOT_EFFECT, getSlot()), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT upload");
            } else {
                AudioUtilities.execute(() -> EXTEfx.alAuxiliaryEffectSloti(aux.getSlot(), EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECTSLOT_NULL), () -> "ReverbEffectSlot EXTEfx.AL_EFFECTSLOT_EFFECT null");
            }
        }
    }
}