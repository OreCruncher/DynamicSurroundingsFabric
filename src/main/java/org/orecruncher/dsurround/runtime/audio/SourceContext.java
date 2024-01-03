package org.orecruncher.dsurround.runtime.audio;

import com.google.common.base.MoreObjects;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.EXTEfx;
import org.orecruncher.dsurround.lib.random.SplitMax;
import org.orecruncher.dsurround.runtime.audio.effects.Effects;
import org.orecruncher.dsurround.runtime.audio.effects.LowPassData;
import org.orecruncher.dsurround.runtime.audio.effects.SourcePropertyFloat;

import java.util.concurrent.Callable;

public final class SourceContext implements Callable<Void> {

    // Lightweight randomizer used to distribute updates across an interval
    private static final SplitMax RANDOM = new SplitMax();
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

    private final int sourceId;

    private SoundInstance sound;
    private Vec3 pos;
    private SoundSource category = SoundSource.MASTER;

    private boolean isEnabled;
    private int updateCount;

    public SourceContext(int sourceId) {
        this.sourceId = sourceId;
        this.lowPass0 = new LowPassData();
        this.lowPass1 = new LowPassData();
        this.lowPass2 = new LowPassData();
        this.lowPass3 = new LowPassData();
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
                // Upload the data
                Effects.filter0.apply(this.sourceId, this.lowPass0, 0, Effects.auxSlot0);
                Effects.filter1.apply(this.sourceId, this.lowPass1, 1, Effects.auxSlot1);
                Effects.filter2.apply(this.sourceId, this.lowPass2, 2, Effects.auxSlot2);
                Effects.filter3.apply(this.sourceId, this.lowPass3, 3, Effects.auxSlot3);
                Effects.direct.apply(this.sourceId, this.direct);

                this.airAbsorb.apply(sourceId);

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
        this.updateCount = RANDOM.nextInt(UPDATE_FEQUENCY_TICKS);
        this.tick();
    }

    private void updateImpl() {
        try {
            //if (this.sound.getId().getPath().contains("stone"))
                this.fxProcessor.calculate(SoundFXProcessor.getWorldContext());
        } catch (final Throwable ignore) {
            // Suppress.  Times that I have seen this fire was due to a world unloading and the background
            // processing threads tripping over dead objects.
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