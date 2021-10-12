package org.orecruncher.dsurround.runtime.audio.effects;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.Client;

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
            execute(() -> this.slot = this.factory.get(), () -> "Slot factory get");
            execute(this::init0, () -> "Slot init0");
        }
    }

    public final void deinitialize() {
        this.slot = EXTEfx.AL_EFFECTSLOT_NULL;
    }

    protected abstract void init0();

    public int getSlot() {
        return this.slot;
    }

    protected void execute(final Runnable func) {
        execute(func, null);
    }

    protected void execute(final Runnable func, @Nullable final Supplier<String> context) {
        func.run();
        final int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorName = AL10.alGetString(error);
            if (StringUtils.isEmpty(errorName))
                errorName = Integer.toString(error);

            String msg = null;
            if (context != null)
                msg = context.get();
            if (msg == null)
                msg = "NONE";

            Client.LOGGER.warn(String.format("OpenAL Error: %s [%s]", errorName, msg));
        }
    }
}