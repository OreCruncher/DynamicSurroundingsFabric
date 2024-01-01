package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

public final class SoundFactoryBuilder {

    private final SoundEvent soundEvent;
    private float minVolume = 1F;
    private float maxVolume = 1F;
    private float minPitch = 1F;
    private float maxPitch = 1F;
    private SoundSource category = SoundSource.AMBIENT;
    private boolean isRepeatable = false;
    private int repeatDelay = 0;
    private boolean global = false;
    private SoundInstance.Attenuation attenuationType = SoundInstance.Attenuation.LINEAR;

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

    public SoundFactoryBuilder category(SoundSource category) {
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

    public SoundFactoryBuilder attenuation(SoundInstance.Attenuation attenuation) {
        this.attenuationType = attenuation;
        this.global = attenuation == SoundInstance.Attenuation.NONE;
        return this;
    }

    public SoundFactoryBuilder global() {
        this.attenuationType = SoundInstance.Attenuation.NONE;
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

    private SimpleSoundInstance createAsAdditional() {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                RandomSource.create(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuationType,
                0.0D,
                0.0D,
                0.0D,
                true);
    }

    private EntityBoundSoundInstance createAtEntity(Entity entity) {
        return new EntityBoundSoundInstance(
                this.soundEvent,
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                entity,
                XorShiftRandom.current().nextLong()
        );
    }

    private SimpleSoundInstance createAtLocation(Vec3 position) {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                RandomSource.create(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuationType,
                position.x(),
                position.y(),
                position.z(),
                this.global);
    }

    public static SoundFactoryBuilder create(String soundEventId) {
        var se = ContainerManager.resolve(ISoundLibrary.class).getSound(soundEventId);
        return create(se);
    }

    public static SoundFactoryBuilder create(ResourceLocation soundEventId) {
        var se = ContainerManager.resolve(ISoundLibrary.class).getSound(soundEventId);
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
        public SimpleSoundInstance createAsMood(Entity entity, int minRange, int maxRange) {
            var offset = MathStuff.randomPoint(minRange, maxRange);
            var position = entity.getEyePosition().add(offset);
            return this.builder.createAtLocation(position);
        }

        @Override
        public SimpleSoundInstance createAsAdditional() {
            return this.builder.createAsAdditional();
        }

        @Override
        public SimpleSoundInstance createAtLocation(BlockPos pos) {
            return this.builder.createAtLocation(Vec3.atCenterOf(pos));
        }

        @Override
        public EntityBoundSoundInstance createAtEntity(Entity entity) {
            return this.builder.createAtEntity(entity);
        }

        @Override
        public SimpleSoundInstance createAtLocation(Vec3 position) {
            return this.builder.createAtLocation(position);
        }

        @Override
        public int hashCode() {
            return this.builder.soundEvent.getLocation().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Factory f && this.builder.soundEvent.getLocation().equals(f.builder.soundEvent.getLocation());
        }

        @Override
        public int compareTo(@NotNull ISoundFactory o) {
            return this.builder.soundEvent.getLocation().compareTo(o.getSoundEvent().getLocation());
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("SoundEvent", this.builder.soundEvent.getLocation())
                    .toString();
        }
    }
}
