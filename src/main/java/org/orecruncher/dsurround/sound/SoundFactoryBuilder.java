package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

@Environment(EnvType.CLIENT)
public final class SoundFactoryBuilder {

    private static final Vec3d BLOCK_CENTER_ADJUST = new Vec3d(0.5D, 0.5D, 0.5D);

    private final SoundEvent soundEvent;
    private float minVolume = 1F;
    private float maxVolume = 1F;
    private float minPitch = 1F;
    private float maxPitch = 1F;
    private SoundCategory category = SoundCategory.AMBIENT;
    private boolean isRepeatable = false;
    private int repeatDelay = 0;
    private boolean global = false;
    private SoundInstance.AttenuationType attenuationType = SoundInstance.AttenuationType.LINEAR;

    SoundFactoryBuilder(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
    }

    public SoundFactoryBuilder volume(float vol) {
        this.minVolume = this.maxVolume = vol;
        return this;
    }

    public SoundFactoryBuilder volumeRange(float min, float max) {
        this.minVolume = min;
        this.maxVolume = max;
        return this;
    }

    public SoundFactoryBuilder pitch(float pitch) {
        this.minPitch = this.maxPitch = pitch;
        return this;
    }

    public SoundFactoryBuilder pitchRange(float min, float max) {
        this.minPitch = min;
        this.maxPitch = max;
        return this;
    }

    public SoundFactoryBuilder category(SoundCategory category) {
        this.category = category;
        return this;
    }

    public SoundFactoryBuilder repeatable() {
        this.isRepeatable = true;
        this.repeatDelay = 0;
        return this;
    }

    public SoundFactoryBuilder repeatable(int delay) {
        this.isRepeatable = true;
        this.repeatDelay = delay;
        return this;
    }

    public SoundFactoryBuilder attenuation(SoundInstance.AttenuationType attenuation) {
        this.attenuationType = attenuation;
        this.global = attenuation == SoundInstance.AttenuationType.NONE;
        return this;
    }

    public SoundFactoryBuilder global() {
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.global = true;
        return this;
    }

    public ISoundFactory build() {
        return new Factory(this);
    }

    private float generate(float min, float max) {
        var delta = max - min;
        if (Float.compare(delta, 0) == 0)
            return min;
        return (float) (XorShiftRandom.current().nextDouble() * delta + min);
    }

    private BackgroundSoundLoop createBackgroundSoundLoop() {
        return new BackgroundSoundLoop(this.soundEvent)
                .setVolume(this.generate(this.minVolume, this.maxVolume))
                .setPitch(this.generate(this.minPitch, this.maxPitch));
    }

    private BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos) {
        return new BackgroundSoundLoop(this.soundEvent, pos)
                .setVolume(this.generate(this.minVolume, this.maxVolume))
                .setPitch(this.generate(this.minPitch, this.maxPitch));
    }

    private PositionedSoundInstance createAsAdditional() {
        return new PositionedSoundInstance(
                this.soundEvent.getId(),
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                Random.create(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuationType,
                0.0D,
                0.0D,
                0.0D,
                true);
    }

    private EntityTrackingSoundInstance createAtEntity(Entity entity) {
        return new EntityTrackingSoundInstance(
                this.soundEvent,
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                entity,
                RandomSeed.getSeed()
        );
    }

    private PositionedSoundInstance createAtLocation(Vec3d position) {
        return new PositionedSoundInstance(
                this.soundEvent.getId(),
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                Random.create(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuationType,
                position.getX(),
                position.getY(),
                position.getZ(),
                this.global);
    }

    public static SoundFactoryBuilder create(String soundEventId) {
        var se = SoundLibrary.getSound(soundEventId);
        return create(se);
    }

    public static SoundFactoryBuilder create(Identifier soundEventId) {
        var se = SoundLibrary.getSound(soundEventId);
        return create(se);
    }

    public static SoundFactoryBuilder create(SoundEvent soundEvent) {
        return new SoundFactoryBuilder(soundEvent);
    }

    private record Factory(SoundFactoryBuilder builder) implements Comparable<ISoundFactory>, ISoundFactory {

        @Override
        public SoundEvent getSoundEvent() {
            return this.builder.soundEvent;
        }

        @Override
        public BackgroundSoundLoop createBackgroundSoundLoop() {
            return this.builder.createBackgroundSoundLoop();
        }

        @Override
        public BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos) {
            return this.builder.createBackgroundSoundLoopAt(pos);
        }

        @Override
        public PositionedSoundInstance createAsMood(Entity entity, int minRange, int maxRange) {
            var offset = MathStuff.randomPoint(minRange, maxRange);
            var position = entity.getEyePos().add(offset);
            return this.builder.createAtLocation(position);
        }

        @Override
        public PositionedSoundInstance createAsAdditional() {
            return this.builder.createAsAdditional();
        }

        @Override
        public PositionedSoundInstance createAtLocation(BlockPos pos) {
            return this.builder.createAtLocation(Vec3d.of(pos).add(BLOCK_CENTER_ADJUST));
        }

        @Override
        public EntityTrackingSoundInstance createAtEntity(Entity entity) {
            return this.builder.createAtEntity(entity);
        }

        @Override
        public PositionedSoundInstance createAtLocation(Vec3d position) {
            return this.builder.createAtLocation(position);
        }

        @Override
        public int hashCode() {
            return this.builder.soundEvent.hashCode();
        }

        @Override
        public int compareTo(@NotNull ISoundFactory o) {
            return this.builder.soundEvent.getId().compareTo(o.getSoundEvent().getId());
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("SoundEvent", this.builder.soundEvent.getId())
                    .toString();
        }
    }
}
