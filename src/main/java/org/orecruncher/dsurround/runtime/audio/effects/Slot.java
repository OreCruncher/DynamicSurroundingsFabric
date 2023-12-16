package org.orecruncher.dsurround.runtime.audio.effects;

import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

import java.util.function.Supplier;

public abstract class Slot {

    private final Supplier<Integer> factory;
    private int slot = EXTEfx.AL_EFFECTSLOT_NULL;

    public Slot(final Supplier<Integer> slotFactory) {
        this.factory = slotFactory;
    }

    public boolean isInitialized() {
        return this.slot != EXTEfx.AL_EFFECTSLOT_NULL;
    }

    public final void initialize() {
        if (this.slot == EXTEfx.AL_EFFECTSLOT_NULL) {
            AudioUtilities.execute(() -> this.slot = this.factory.get(), () -> "Slot factory get");
            AudioUtilities.execute(this::init0, () -> "Slot init0");
        }
    }

    public final void deinitialize() {
        this.slot = EXTEfx.AL_EFFECTSLOT_NULL;
    }

    protected abstract void init0();

    public int getSlot() {
        return this.slot;
    }
}