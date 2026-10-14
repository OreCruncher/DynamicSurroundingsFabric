package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

/**
 * The OpenAL effects (EFX) used by enhanced sounds in one OpenAL context: four reverbs of increasing length, each in
 * an auxiliary effect slot that sounds send to, and low-pass filters for those sends and for each sound's direct path.
 * The game's sounds have one; Simple Voice Chat plays voices in a context of its own, which gets another.
 * <p>
 * A context can have fewer sends than reverbs (voice chat's, if the device won't give it more): sounds then only send
 * to the longest reverbs, as many as it has, which are what makes a space sound large.
 * <p>
 * The filter objects are shared by all sounds in the context: a filter's settings are copied into a source when it is
 * attached, so each upload sets the filter and attaches it in turn. Every call must be made on a thread where the
 * context is current, and by one thread at a time. Source IDs are only unique within a context: made from another, an
 * upload would change that context's source with the same ID, so uploads check (with assertions on).
 * <p>
 * Every OpenAL call is checked; an error throws an IllegalStateException naming the step (see
 * {@link AudioUtilities#validate}).
 */
public final class Efx {

    /**
     * Number of reverbs, and so the most sends a sound uses.
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

    // The context the effects were made in, which every upload must be made in
    private final long context;
    private final int sends;
    // The send that carries each reverb, or -1 if none does
    private final int[] sendOfReverb = new int[SENDS];
    private final int[] auxSlots;
    private final int[] sendFilters;
    private final int directFilter;

    private Efx(int sends) {
        this.context = ALC10.alcGetCurrentContext();
        this.sends = sends;
        this.auxSlots = new int[sends];
        this.sendFilters = new int[sends];
        for (int reverb = 0; reverb < SENDS; reverb++)
            this.sendOfReverb[reverb] = sendOfReverb(reverb, sends);

        for (int i = 0; i < sends; i++) {
            this.auxSlots[i] = EXTEfx.alGenAuxiliaryEffectSlots();
            AudioUtilities.validate("Efx generate aux slot");
            EXTEfx.alAuxiliaryEffectSloti(this.auxSlots[i], EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);
            AudioUtilities.validate("Efx aux slot send auto");

            int reverbEffect = EXTEfx.alGenEffects();
            AudioUtilities.validate("Efx generate reverb");
            // Setting the type resets every parameter to its default; only these differ
            EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            var reverb = REVERBS[reverbOfSend(i, sends)];
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_TIME, reverb.decayTime());
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAIN, reverb.gain());
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAINHF, reverb.gainHF());
            AudioUtilities.validate("Efx configure reverb");

            // The slot keeps its own copy of the effect's settings
            EXTEfx.alAuxiliaryEffectSloti(this.auxSlots[i], EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);
            AudioUtilities.validate("Efx load reverb into aux slot");

            this.sendFilters[i] = newLowPassFilter();
        }
        this.directFilter = newLowPassFilter();
    }

    /**
     * Creates the effects in the OpenAL context current on this thread. They go with the context, so there is
     * nothing to release (but see {@link #mute}).
     *
     * @param sends the auxiliary sends each source has in the context; up to {@link #SENDS} are used
     */
    public static Efx create(int sends) {
        return new Efx(Math.max(0, Math.min(sends, SENDS)));
    }

    /**
     * The sends sounds use in this context, at most {@link #SENDS}.
     */
    public int sends() {
        return this.sends;
    }

    /**
     * Whether its context is the one current on this thread (the thread's own context, if it has one, else the
     * process's).
     */
    public boolean isCurrent() {
        return ALC10.alcGetCurrentContext() == this.context;
    }

    /**
     * The reverb a send carries, in a context with this many sends: the longest reverbs, as many as there are sends.
     */
    static int reverbOfSend(int send, int sends) {
        return SENDS - sends + send;
    }

    /**
     * The send that carries a reverb, in a context with this many sends, or -1 if none does.
     */
    static int sendOfReverb(int reverb, int sends) {
        int send = reverb - (SENDS - sends);
        return send < 0 ? -1 : send;
    }

    /**
     * Mutes the reverbs, which ring on for up to a few seconds after their sounds stop: destroyed while ringing, a
     * context cuts them off mid-stream. The mixer applies it on its next update, so wait for that before destroying.
     */
    public void mute() {
        assert this.isCurrent() : "Efx used outside the OpenAL context it was made in";
        for (int slot : this.auxSlots)
            EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, 0F);
        AudioUtilities.validate("Efx mute aux slots");
    }

    /**
     * How long the device takes to mix one update, in milliseconds.
     */
    public long updateMillis() {
        int refresh = ALC10.alcGetInteger(ALC10.alcGetContextsDevice(this.context), ALC10.ALC_REFRESH);
        // OpenAL Soft's default is about 50 updates a second
        return 1000L / (refresh > 0 ? refresh : 50);
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
     * @param reverbs one per reverb ({@link #SENDS}), shortest first; those without a send in this context are skipped
     * @param direct  the direct path
     */
    public void uploadIfChanged(int sourceId, LowPassData[] reverbs, LowPassData direct) {
        assert this.isCurrent() : "Efx used outside the OpenAL context it was made in";
        for (int reverb = 0; reverb < SENDS; reverb++) {
            // Taken even when unused, so it doesn't stay pending
            int send = this.sendOfReverb[reverb];
            if (reverbs[reverb].takeChanged() && send >= 0)
                this.uploadSend(sourceId, send, reverbs[reverb]);
        }
        if (direct.takeChanged())
            this.uploadDirect(sourceId, direct);
    }

    private void uploadSend(int sourceId, int send, LowPassData data) {
        if (data.isEnabled()) {
            int filter = this.sendFilters[send];
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, data.gain());
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, data.gainHF());
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, this.auxSlots[send], send, filter);
            AudioUtilities.validate("Efx send filter upload");
        } else {
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, send, EXTEfx.AL_FILTER_NULL);
            AudioUtilities.validate("Efx send filter clear");
        }
    }

    private void uploadDirect(int sourceId, LowPassData data) {
        if (data.isEnabled()) {
            EXTEfx.alFilterf(this.directFilter, EXTEfx.AL_LOWPASS_GAIN, data.gain());
            EXTEfx.alFilterf(this.directFilter, EXTEfx.AL_LOWPASS_GAINHF, data.gainHF());
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, this.directFilter);
            AudioUtilities.validate("Efx direct filter upload");
        } else {
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            AudioUtilities.validate("Efx direct filter clear");
        }
    }
}
