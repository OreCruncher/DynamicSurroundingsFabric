package org.orecruncher.dsurround.runtime.audio;

import com.google.common.base.MoreObjects;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.audio.effects.Efx;
import org.orecruncher.dsurround.runtime.audio.effects.LowPassData;
import org.orecruncher.dsurround.runtime.audio.effects.SourcePropertyFloat;

import java.util.concurrent.Callable;

/**
 * The enhanced sound processing of one sound playing on an OpenAL source: its filters, calculated by the worker
 * threads ({@link #call()}) from where its {@link ISoundEmitter} is, and uploaded to the source by the thread that
 * plays it ({@link #tick()}).
 */
public final class SourceContext implements Callable<Void> {

    private static final LogThrottle<Object> CALCULATION_ERRORS = LogThrottle.firstN(Library.LOGGER, "sound effect calculation errors", null, 10);

    // Frequency of sound effect updates in thread schedule ticks.  Works out to be 3 times a second.
    private static final int UPDATE_FEQUENCY_TICKS = 7;

    private final Object sync = new Object();
    // One per reverb, shortest first; Efx decides which of the context's sends carries each
    private final LowPassData[] reverbs = new LowPassData[Efx.SENDS];
    private final LowPassData direct;
    private final SourcePropertyFloat airAbsorb;
    private final SourceAcoustics acoustics;

    private final int sourceId;
    private final Efx efx;
    private final ISoundEmitter emitter;

    // The emitter's position, taken at the start of each calculation so it holds still during it
    private volatile Vec3 pos;

    // Cleared by disable(), on the thread playing the sound; read by the worker threads
    private volatile boolean isEnabled = true;
    private int updateCount;

    /**
     * @param sourceId the OpenAL source the sound plays on
     * @param efx      the effects of the OpenAL context the source belongs to
     * @param emitter  what the sound comes from
     */
    public SourceContext(int sourceId, Efx efx, ISoundEmitter emitter) {
        this.sourceId = sourceId;
        this.efx = efx;
        this.emitter = emitter;
        for (int i = 0; i < this.reverbs.length; i++)
            this.reverbs[i] = new LowPassData();
        this.direct = new LowPassData();
        this.airAbsorb = new SourcePropertyFloat(EXTEfx.AL_AIR_ABSORPTION_FACTOR, EXTEfx.AL_DEFAULT_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MIN_AIR_ABSORPTION_FACTOR, EXTEfx.AL_MAX_AIR_ABSORPTION_FACTOR);
        this.pos = emitter.position();
        this.acoustics = new SourceAcoustics(this);
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

    /**
     * Stops processing the sound and takes its filters off, for a source that plays on without them. Called where
     * {@link #tick()} is.
     */
    public void disable() {
        synchronized (this.sync()) {
            this.isEnabled = false;
            for (var reverb : this.reverbs)
                reverb.disable();
            this.direct.disable();
            this.airAbsorb.setProcess(false);
            this.efx.uploadIfChanged(this.sourceId, this.reverbs, this.direct);
            this.airAbsorb.apply(this.sourceId);
            AudioUtilities.validate("SourceHandler::disable");
        }
    }

    /**
     * The send filter for a reverb, 0 (the shortest) to {@link Efx#SENDS} - 1.
     */
    public LowPassData getReverb(int reverb) {
        return this.reverbs[reverb];
    }

    public LowPassData getDirect() {
        return this.direct;
    }

    public SourcePropertyFloat getAirAbsorb() {
        return this.airAbsorb;
    }

    /**
     * Where the sound was at the start of the current calculation.
     */
    public Vec3 getPosition() {
        return this.pos;
    }

    public SoundSource getCategory() {
        return this.emitter.category();
    }

    /**
     * Whether the listener is close enough to hear the sound.
     */
    public boolean inRange(final Vec3 listener) {
        return this.emitter.inRange(listener);
    }

    /**
     * Uploads what changed. Called on the thread that plays the sound, where its OpenAL context is current: the sound
     * engine's thread or, for a voice, its speaker thread. Do not call from the client thread or bad things can
     * happen.
     */
    public void tick() {
        if (this.isEnabled()) {
            synchronized (this.sync()) {
                // Upload what changed since the last tick
                this.efx.uploadIfChanged(this.sourceId, this.reverbs, this.direct);
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
     * Calculates and uploads straight away, when the sound starts playing. Called where {@link #tick()} is.
     */
    public void exec() {
        this.captureState();
        this.updateImpl();
        this.updateCount = Randomizer.current().nextInt(UPDATE_FEQUENCY_TICKS);
        this.tick();
    }

    private void updateImpl() {
        try {
            this.acoustics.calculate(EnhancedSoundProcessor.getWorldContext());
        } catch (final Exception e) {
            // Usually a world unloading while the background threads are using it, which is harmless. Logged
            // (a limited number of times) so that anything else, like a calculation that always fails, is seen.
            CALCULATION_ERRORS.error(e, "Unable to calculate sound effects for %s", this.emitter.describe());
        }
    }

    private void captureState() {
        this.pos = this.emitter.position();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(this.emitter.describe())
                .toString();
    }

}
