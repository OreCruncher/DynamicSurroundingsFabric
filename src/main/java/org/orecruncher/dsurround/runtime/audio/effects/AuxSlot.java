package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public class AuxSlot extends Slot {

    public AuxSlot() {
        super(EXTEfx::alGenAuxiliaryEffectSlots);
    }

    @Override
    protected void init0() {
        AudioUtilities.execute(() -> EXTEfx.alAuxiliaryEffectSloti(getSlot(), EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE), () -> "AuxSlot EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO");
    }
}