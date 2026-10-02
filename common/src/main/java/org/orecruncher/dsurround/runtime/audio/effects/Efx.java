package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

import java.util.Arrays;

/**
 * The OpenAL effects (EFX) used by enhanced sounds: four reverbs of increasing length, each in an auxiliary effect
 * slot that sounds send to, and low-pass filters for those sends and for each sound's direct path.
 * <p>
 * The filter objects are shared by all sounds: a filter's settings are copied into a source when it is attached,
 * so each upload sets the filter and attaches it in turn. All calls are made on the sound engine's thread.
 * <p>
 * Every OpenAL call is checked; an error throws an IllegalStateException naming the step (see
 * {@link AudioUtilities#validate}).
 */
public final class Efx {

    /**
     * Number of reverb sends.
     */
    public static final int SENDS = 4;

    private static final float GLOBAL_REVERB_MULTIPLIER = 0.7F;

    /**
     * A reverb: how long it rings, how loud and how bright it is. Its other parameters are OpenAL's defaults.
     */
    private record Reverb(float decayTime, float gain, float gainHF) {
    }

    private static final Reverb[] REVERBS = {
            new Reverb(0.15F, 0.2F * 0.85F * GLOBAL_REVERB_MULTIPLIER, 0.99F),
            new Reverb(0.55F, 0.3F * 0.85F * GLOBAL_REVERB_MULTIPLIER, 0.99F),
            new Reverb(1.68F, 0.5F * 0.85F * GLOBAL_REVERB_MULTIPLIER, 0.99F),
            new Reverb(4.142F, 0.4F * 0.85F * GLOBAL_REVERB_MULTIPLIER, 0.89F)
    };

    private static final int[] auxSlots = new int[SENDS];
    private static final int[] reverbEffects = new int[SENDS];
    private static final int[] sendFilters = new int[SENDS];
    private static int directFilter;
    private static boolean initialized;

    private Efx() {
    }

    /**
     * Creates the effects. Call when the sound engine starts; calling again does nothing until
     * {@link #deinitialize()}.
     */
    public static void initialize() {
        if (initialized)
            return;

        for (int i = 0; i < SENDS; i++) {
            auxSlots[i] = EXTEfx.alGenAuxiliaryEffectSlots();
            AudioUtilities.validate("Efx generate aux slot");
            EXTEfx.alAuxiliaryEffectSloti(auxSlots[i], EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);
            AudioUtilities.validate("Efx aux slot send auto");

            reverbEffects[i] = EXTEfx.alGenEffects();
            AudioUtilities.validate("Efx generate reverb");
            // Setting the type resets every parameter to its default; only these differ
            EXTEfx.alEffecti(reverbEffects[i], EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            var reverb = REVERBS[i];
            EXTEfx.alEffectf(reverbEffects[i], EXTEfx.AL_EAXREVERB_DECAY_TIME, reverb.decayTime());
            EXTEfx.alEffectf(reverbEffects[i], EXTEfx.AL_EAXREVERB_GAIN, reverb.gain());
            EXTEfx.alEffectf(reverbEffects[i], EXTEfx.AL_EAXREVERB_GAINHF, reverb.gainHF());
            AudioUtilities.validate("Efx configure reverb");

            EXTEfx.alAuxiliaryEffectSloti(auxSlots[i], EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffects[i]);
            AudioUtilities.validate("Efx load reverb into aux slot");

            sendFilters[i] = newLowPassFilter();
        }
        directFilter = newLowPassFilter();
        initialized = true;
    }

    /**
     * Forgets the effects when the sound engine shuts down. The OpenAL objects go with the engine's context.
     */
    public static void deinitialize() {
        initialized = false;
        Arrays.fill(auxSlots, 0);
        Arrays.fill(reverbEffects, 0);
        Arrays.fill(sendFilters, 0);
        directFilter = 0;
    }

    private static int newLowPassFilter() {
        int filter = EXTEfx.alGenFilters();
        AudioUtilities.validate("Efx generate filter");
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        AudioUtilities.validate("Efx set filter type");
        return filter;
    }

    /**
     * Sends a sound's filter settings to OpenAL, for those that changed since the last upload.
     *
     * @param sends  one per reverb send
     * @param direct the direct path
     */
    public static void uploadIfChanged(int sourceId, LowPassData[] sends, LowPassData direct) {
        if (!initialized)
            return;
        for (int send = 0; send < SENDS; send++) {
            if (sends[send].takeChanged())
                uploadSend(sourceId, send, sends[send]);
        }
        if (direct.takeChanged())
            uploadDirect(sourceId, direct);
    }

    private static void uploadSend(int sourceId, int send, LowPassData data) {
        if (data.isEnabled()) {
            int filter = sendFilters[send];
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, data.gain());
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, data.gainHF());
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxSlots[send], send, filter);
            AudioUtilities.validate("Efx send filter upload");
        } else {
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, send, EXTEfx.AL_FILTER_NULL);
            AudioUtilities.validate("Efx send filter clear");
        }
    }

    private static void uploadDirect(int sourceId, LowPassData data) {
        if (data.isEnabled()) {
            EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN, data.gain());
            EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF, data.gainHF());
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, directFilter);
            AudioUtilities.validate("Efx direct filter upload");
        } else {
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            AudioUtilities.validate("Efx direct filter clear");
        }
    }
}
