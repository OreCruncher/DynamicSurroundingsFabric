package org.orecruncher.dsurround.runtime.audio;

import com.google.common.base.MoreObjects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.LCGRandom;
import org.orecruncher.dsurround.runtime.audio.effects.Effects;
import org.orecruncher.dsurround.runtime.audio.effects.LowPassData;
import org.orecruncher.dsurround.runtime.audio.effects.SourcePropertyFloat;

import java.util.concurrent.Callable;

public final class SourceContext implements Callable<Void> {

    private static final IModLog LOGGER = Client.LOGGER.createChild(SourceContext.class);
    // Lightweight randomizer used to distribute updates across an interval
    private static final LCGRandom RANDOM = new LCGRandom();
    // Frequency of sound effect updates in thread schedule ticks.  Works out to be 3 times a second.
    private static final int UPDATE_FEQUENCY_TICKS = 7;

    private final Object sync = new Object();
    private final LowPassData lowPass0;
    private final LowPassData lowPass1;
    private final LowPassData lowPass2;
    private final LowPassData lowPass3;
    private final LowPassData direct;
    private final SourcePropertyFloat airAbsorb;
    private final SoundFXUtils fxProcessor;

    private SoundInstance sound;
    private Vec3d pos;
    private SoundCategory category = SoundCategory.MASTER;

    private boolean isEnabled;
    private int updateCount;

    public SourceContext() {
        this.lowPass0 = new LowPassData();
        this.lowPass1 = new LowPassData();
        this.lowPass2 = new LowPassData();
        this.lowPass3 = new LowPassData();
        this.direct = new LowPassData();
        this.airAbsorb = new SourcePropertyFloat(EXTEfx.AL_AIR_ABSORPTION_FACTOR, EXTEfx.AL_DEFAULT_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MIN_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MAX_AIR_ABSORPTION_FACTOR);
        this.pos = Vec3d.ZERO;
        this.fxProcessor = new SoundFXUtils(this);
    }

    public Object sync() {
        return this.sync;
    }

    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void enable() {
        this.isEnabled = true;
    }

    public LowPassData getLowPass0() {
        return this.lowPass0;
    }

    public LowPassData getLowPass1() {
        return this.lowPass1;
    }

    public LowPassData getLowPass2() {
        return this.lowPass2;
    }

    public LowPassData getLowPass3() {
        return this.lowPass3;
    }

    public LowPassData getDirect() {
        return this.direct;
    }

    public SourcePropertyFloat getAirAbsorb() {
        return this.airAbsorb;
    }

    public Vec3d getPosition() {
        return this.pos;
    }

    
    public SoundCategory getCategory() {
        return this.category;
    }

    public void attachSound( final SoundInstance sound) {
        this.sound = sound;
        this.category = sound.getCategory();
        captureState();
    }

    @Nullable
    public SoundInstance getSound() {
        return this.sound;
    }

    /**
     * Called on the SoundSource update thread when updating status.  Do not call from the client thread or bad things
     * can happen.
     */
    public void tick(final int sourceId) {
        if (isEnabled()) {
            synchronized (this.sync()) {
                // Upload the data
                Effects.filter0.apply(sourceId, this.lowPass0, 0, Effects.auxSlot0);
                Effects.filter1.apply(sourceId, this.lowPass1, 1, Effects.auxSlot1);
                Effects.filter2.apply(sourceId, this.lowPass2, 2, Effects.auxSlot2);
                Effects.filter3.apply(sourceId, this.lowPass3, 3, Effects.auxSlot3);
                Effects.direct.apply(sourceId, this.direct);

                this.airAbsorb.apply(sourceId);

                SoundFXProcessor.validate("SourceHandler::tick");
            }
        }
    }

    /**
     * Called by the sound processing thread when scheduling work items for sound updates.  This routine should only
     * be called by the background thread.
     *
     * @return true the work item should be scheduled; false otherwise
     */
    public boolean shouldExecute() {
        // If brand new randomize the start time
        if (this.updateCount == 0) {
            this.updateCount = RANDOM.nextInt(UPDATE_FEQUENCY_TICKS);
        }
        return (this.updateCount++ % UPDATE_FEQUENCY_TICKS) == 0;
    }

    @Override
    public Void call() throws Exception {
        captureState();
        updateImpl();
        return null;
    }

    /**
     * Called by the thread pool when executing the task
     *
     */
    public final void exec() {
        captureState();
        updateImpl();
    }

    private void updateImpl() {
        try {
            this.fxProcessor.calculate(SoundFXProcessor.getWorldContext());
        } catch( final Throwable t) {
            LOGGER.error(t, "Error processing SoundContext %s", toString());
        }
    }

    private void captureState() {
        if (this.sound != null) {
            this.pos = new Vec3d(this.sound.getX(), this.sound.getY(), this.sound.getZ());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(MinecraftClient.format(this.sound))
                .toString();
    }

}