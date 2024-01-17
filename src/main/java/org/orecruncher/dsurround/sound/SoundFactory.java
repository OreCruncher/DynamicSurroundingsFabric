package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Optional;

import static org.orecruncher.dsurround.sound.SoundCodecHelpers.SOUND_PROPERTY_RANGE;

public record SoundFactory(
        Optional<ResourceLocation> location,
        SoundEvent soundEvent,
        FloatProvider volume,
        FloatProvider pitch,
        SoundSource category,
        boolean isRepeatable,
        int repeatDelay,
        boolean global,
        SoundInstance.Attenuation attenuation) implements Comparable<ISoundFactory>, ISoundFactory {

    public static final Codec<SoundFactory> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    IdentityUtils.CODEC.optionalFieldOf("location").forGetter(SoundFactory::location),
                    SoundCodecHelpers.SOUND_EVENT_CODEC.fieldOf("soundEvent").forGetter(SoundFactory::soundEvent),
                    SOUND_PROPERTY_RANGE.optionalFieldOf("volume", ConstantFloat.of(1F)).forGetter(SoundFactory::volume),
                    SOUND_PROPERTY_RANGE.optionalFieldOf("pitch", ConstantFloat.of(1F)).forGetter(SoundFactory::pitch),
                    SoundCodecHelpers.SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundSource.AMBIENT).forGetter(SoundFactory::category),
                    Codec.BOOL.optionalFieldOf("isRepeatable", false).forGetter(SoundFactory::isRepeatable),
                    Codec.INT.optionalFieldOf("repeatDelay", 0).forGetter(SoundFactory::repeatDelay),
                    Codec.BOOL.optionalFieldOf("global", false).forGetter(SoundFactory::global),
                    SoundCodecHelpers.ATTENUATION_CODEC.optionalFieldOf("attenuation", SoundInstance.Attenuation.LINEAR).forGetter(SoundFactory::attenuation)
            ).apply(instance, SoundFactory::new));

    @Override
    public ResourceLocation getLocation() {
        return this.location.orElse(this.soundEvent.getLocation());
    }

    @Override
    public BackgroundSoundLoop createBackgroundSoundLoop() {
        return new BackgroundSoundLoop(this.soundEvent)
                .setVolume(this.getVolume())
                .setPitch(this.getPitch());
    }

    @Override
    public BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos) {
        return new BackgroundSoundLoop(this.soundEvent, pos)
                .setVolume(this.getVolume())
                .setPitch(this.getPitch());
    }

    @Override
    public SimpleSoundInstance createAsAdditional() {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.getVolume(),
                this.getPitch(),
                Randomizer.current(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuation,
                0.0D,
                0.0D,
                0.0D,
                true);
    }

    @Override
    public EntityBoundSoundInstance attachToEntity(Entity entity) {
        return new EntityBoundSoundInstance(
                this.soundEvent,
                this.category,
                this.getVolume(),
                this.getPitch(),
                entity,
                Randomizer.current().nextLong()
        );
    }

    @Override
    public SimpleSoundInstance createAtLocation(Vec3 position, float volumeScale) {
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.getVolume() * volumeScale,
                this.getPitch(),
                Randomizer.current(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuation,
                position.x(),
                position.y(),
                position.z(),
                this.global);
    }

    private float getVolume() {
        return this.volume.sample(Randomizer.current());
    }

    private float getPitch() {
        return this.pitch.sample(Randomizer.current());
    }

    @Override
    public int hashCode() {
        return this.getLocation().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        return obj instanceof SoundFactory f && this.getLocation().equals(f.getLocation());
    }

    @Override
    public int compareTo(@NotNull ISoundFactory o) {
        return this.getLocation().compareTo(o.getLocation());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("location", this.getLocation())
                .add("soundEvent", this.soundEvent.getLocation())
                .toString();
    }

    static ISoundFactory from(SoundFactoryBuilder builder) {
        return new SoundFactory(
                Optional.empty(),
                builder.soundEvent,
                builder.volume,
                builder.pitch,
                builder.category,
                builder.isRepeatable,
                builder.repeatDelay,
                builder.global,
                builder.attenuation);
    }
}
