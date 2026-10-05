package org.orecruncher.dsurround.runtime.audio;

import com.google.common.base.MoreObjects;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.audio.effects.Efx;
import org.orecruncher.dsurround.runtime.audio.effects.LowPassData;
import org.orecruncher.dsurround.runtime.audio.effects.SourcePropertyFloat;

import java.util.concurrent.Callable;

public final class SourceContext implements Callable<Void> {

    private static final LogThrottle<Object> CALCULATION_ERRORS = LogThrottle.firstN(Library.LOGGER, "sound effect calculation errors", null, 10);

    // Frequency of sound effect updates in thread schedule ticks.  Works out to be 3 times a second.
    private static final int UPDATE_FEQUENCY_TICKS = 7;

    private final Object sync = new Object();
    private final LowPassData[] sends = new LowPassData[Efx.SENDS];
    private final LowPassData direct;
    private final SourcePropertyFloat airAbsorb;
    private final SoundFXUtils fxProcessor;

    private final int sourceId;

    private SoundInstance sound;
    private Vec3 pos;
    private SoundSource category = SoundSource.MASTER;

    private boolean isEnabled;
    private int updateCount;

    public SourceContext(int sourceId) {
        this.sourceId = sourceId;
        for (int i = 0; i < this.sends.length; i++)
            this.sends[i] = new LowPassData();
        this.direct = new LowPassData();
        this.airAbsorb = new SourcePropertyFloat(EXTEfx.AL_AIR_ABSORPTION_FACTOR, EXTEfx.AL_DEFAULT_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MIN_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MAX_AIR_ABSORPTION_FACTOR);
        this.pos = Vec3.ZERO;
        this.fxProcessor = new SoundFXUtils(this);
    }

    public Object sync() {
        return this.sync;
    }

    public int getId() {
        return this.sourceId;
    }

    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void enable() {
        this.isEnabled = true;
    }

    /**
     * The filter for a reverb send, 0 to {@link Efx#SENDS} - 1.
     */
    public LowPassData getSend(int send) {
        return this.sends[send];
    }

    public LowPassData getDirect() {
        return this.direct;
    }

    public SourcePropertyFloat getAirAbsorb() {
        return this.airAbsorb;
    }

    public Vec3 getPosition() {
        return this.pos;
    }


    public SoundSource getCategory() {
        return this.category;
    }

    public void attachSound(final SoundInstance sound) {
        this.sound = sound;
        this.category = sound.getSource();
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
    public void tick() {
        if (this.isEnabled()) {
            synchronized (this.sync()) {
                // Upload what changed since the last tick
                Efx.uploadIfChanged(this.sourceId, this.sends, this.direct);
                this.airAbsorb.apply(this.sourceId);

                AudioUtilities.validate("SourceHandler::tick");
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
        return (this.updateCount++ % UPDATE_FEQUENCY_TICKS) == 0;
    }

    @Override
    public Void call() {
        this.captureState();
        this.updateImpl();
        return null;
    }

    /**
     * Called by the thread pool when executing the task
     */
    public void exec() {
        this.captureState();
        this.updateImpl();
        this.updateCount = Randomizer.current().nextInt(UPDATE_FEQUENCY_TICKS);
        this.tick();
    }

    private void updateImpl() {
        try {
            this.fxProcessor.calculate(SoundFXProcessor.getWorldContext());
        } catch (final Exception e) {
            // Usually a world unloading while the background threads are using it, which is harmless. Logged
            // (a limited number of times) so that anything else, like a calculation that always fails, is seen.
            CALCULATION_ERRORS.error(e, "Unable to calculate sound effects for %s", this.sound == null ? "?" : this.sound.getIdentifier());
        }
    }

    private void captureState() {
        if (this.sound != null) {
            this.pos = new Vec3(this.sound.getX(), this.sound.getY(), this.sound.getZ());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(AudioUtilities.debugString(this.sound))
                .toString();
    }

}