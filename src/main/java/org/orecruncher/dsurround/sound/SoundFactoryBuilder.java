package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.SampledFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.Randomizer;

@SuppressWarnings("unused")
public final class SoundFactoryBuilder {

    private final SoundEvent soundEvent;
    private SampledFloat volume;
    private SampledFloat pitch;
    private SoundSource category;
    private boolean isRepeatable = false;
    private int repeatDelay = 0;
    private boolean global = false;
    private SoundInstance.Attenuation attenuationType;

    SoundFactoryBuilder(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
        this.volume = ConstantFloat.of(1F);
        this.pitch = ConstantFloat.of(1F);
        this.category = SoundSource.AMBIENT;
        this.attenuationType = SoundInstance.Attenuation.LINEAR;
    }

    SoundFactoryBuilder(SoundFactoryBuilder source) {
        this.soundEvent = source.soundEvent;
        this.volume = source.volume;
        this.pitch = source.pitch;
        this.category = source.category;
        this.isRepeatable = source.isRepeatable;
        this.repeatDelay = source.repeatDelay;
        this.global = source.global;
        this.attenuationType = source.attenuationType;
    }

    public SoundFactoryBuilder volume(float vol) {
        this.volume = ConstantFloat.of(vol);
        return this;
    }

    public SoundFactoryBuilder volume(float min, float max) {
        this.volume = Float.compare(min, max) == 0 ? ConstantFloat.of(min) : UniformFloat.of(min, max);
        return this;
    }

    public SoundFactoryBuilder pitch(float pitch) {
        this.pitch = ConstantFloat.of(pitch);
        return this;
    }

    public SoundFactoryBuilder pitch(float min, float max) {
        this.pitch = Float.compare(min, max) == 0 ? ConstantFloat.of(min) : UniformFloat.of(min, max);
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

    private float getVolume() {
        return this.volume.sample(Randomizer.current());
    }

    private float getPitch() {
        return this.pitch.sample(Randomizer.current());
    }

    private BackgroundSoundLoop createBackgroundSoundLoop() {
        return new BackgroundSoundLoop(this.soundEvent)
                .setVolume(this.getVolume())
                .setPitch(this.getPitch());
    }

    private BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos) {
        return new BackgroundSoundLoop(this.soundEvent, pos)
                .setVolume(this.getVolume())
                .setPitch(this.getPitch());
    }

    private SimpleSoundInstance createAsAdditional() {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.getVolume(),
                this.getPitch(),
                Randomizer.current(),
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
                this.getVolume(),
                this.getPitch(),
                entity,
                Randomizer.current().nextLong()
        );
    }

    private SimpleSoundInstance createAtLocation(Vec3 position, float volumeScale) {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.getVolume() * volumeScale,
                this.getPitch(),
                Randomizer.current(),
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
        public SimpleSoundInstance createAsAdditional() {
            return this.builder.createAsAdditional();
        }

        @Override
        public EntityBoundSoundInstance attachToEntity(Entity entity) {
            return this.builder.createAtEntity(entity);
        }

        @Override
        public SimpleSoundInstance createAtLocation(Vec3 position, float volumeScale) {
            return this.builder.createAtLocation(position, volumeScale);
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
