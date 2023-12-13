package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public class LowPassFilterSlot extends Slot {

    public LowPassFilterSlot() {
        super(EXTEfx::alGenFilters);
    }

    @Override
    protected void init0() {
        EXTEfx.alFilteri(getSlot(), EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
    }

    public void apply(final int sourceId, final LowPassData data) {
        if (isInitialized()) {
            if (data.doProcess()) {
                data.clamp();
                AudioUtilities.execute(() -> EXTEfx.alFilterf(getSlot(), EXTEfx.AL_LOWPASS_GAIN, data.gain), () -> "LowPassFilterSlot EXTEfx.AL_DIRECT_FILTER gain");
                AudioUtilities.execute(() -> EXTEfx.alFilterf(getSlot(), EXTEfx.AL_LOWPASS_GAINHF, data.gainHF), () -> "LowPassFilterSlot EXTEfx.AL_DIRECT_FILTER gainHF");
                AudioUtilities.execute(() -> AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, getSlot()), () -> "LowPassFilterSlot EXTEfx.AL_DIRECT_FILTER upload");
            } else {
                AudioUtilities.execute(() -> AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_EFFECTSLOT_NULL), () -> "LowPassFilterSlot EXTEfx.AL_DIRECT_FILTER null");
            }
        }
    }

    public void apply(final int sourceId, final LowPassData data, final int auxSend, final AuxSlot aux) {
        if (isInitialized()) {
            if (data.doProcess()) {
                data.clamp();
                AudioUtilities.execute(() -> EXTEfx.alFilterf(getSlot(), EXTEfx.AL_LOWPASS_GAIN, data.gain), () -> "LowPassFilterSlot EXTEfx.AL_AUXILIARY_SEND_FILTER gain");
                AudioUtilities.execute(() -> EXTEfx.alFilterf(getSlot(), EXTEfx.AL_LOWPASS_GAINHF, data.gainHF), () -> "LowPassFilterSlot EXTEfx.AL_AUXILIARY_SEND_FILTER gainHF");
                AudioUtilities.execute(() -> AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, aux.getSlot(), auxSend, getSlot()), () -> "LowPassFilterSlot EXTEfx.AL_AUXILIARY_SEND_FILTER upload");
            } else {
                AudioUtilities.execute(() -> AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, auxSend, EXTEfx.AL_FILTER_NULL), () -> "LowPassFilterSlot EXTEfx.AL_AUXILIARY_SEND_FILTER null");
            }
        }
    }
}