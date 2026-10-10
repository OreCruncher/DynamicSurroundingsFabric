package org.orecruncher.dsurround.sound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.registry.IdentityUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        SoundInstance.Attenuation attenuation,
        MusicSettings musicSettings) implements Comparable<ISoundFactory>, ISoundFactory {

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
                    SoundCodecHelpers.ATTENUATION_CODEC.optionalFieldOf("attenuation", SoundInstance.Attenuation.LINEAR).forGetter(SoundFactory::attenuation),
                    MusicSettings.CODEC.optionalFieldOf("music", MusicSettings.DEFAULT).forGetter(SoundFactory::musicSettings)
            ).apply(instance, SoundFactory::new));

    // Music instances by sound event ID and settings: factories that agree share one, and different (or reloaded)
    // settings get their own. The ID, as SoundEvent instances aren't compared by value.
    private record MusicKey(ResourceLocation event, MusicSettings settings) {
    }

    private static final Map<MusicKey, Music> MUSIC_MAP = new ConcurrentHashMap<>();

    /**
     * Keeps {@code global} and {@code attenuation} consistent, whichever the configuration set: a global sound has
     * no attenuation, and a sound without attenuation is global.
     */
    public SoundFactory {
        if (global)
            attenuation = SoundInstance.Attenuation.NONE;
        else if (attenuation == SoundInstance.Attenuation.NONE)
            global = true;
    }

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

    /**
     * {@inheritDoc}
     * <p>
     * A global sound is heard the same everywhere, so it plays at the listener (relative, at 0,0,0) whatever
     * position is given.
     */
    @Override
    public SimpleSoundInstance createAtLocation(double posX, double posY, double posZ, float volumeScale) {
        // The last argument is "relative": a relative sound's position is an offset from the listener, so a global
        // sound at world coordinates would be heard as coming from far off in that direction
        final boolean relative = this.global;
        return new SimpleSoundInstance(
                this.soundEvent.getLocation(),
                this.category,
                this.getVolume() * volumeScale,
                this.getPitch(),
                Randomizer.current(),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuation,
                relative ? 0D : posX,
                relative ? 0D : posY,
                relative ? 0D : posZ,
                relative);
    }

    @Override
    public Music createAsMusic() {
        return MUSIC_MAP.computeIfAbsent(new MusicKey(this.soundEvent.getLocation(), this.musicSettings), key ->
                new Music(Holder.direct(this.soundEvent), key.settings().minDelay(), key.settings().maxDelay(), key.settings().replaceCurrentMusic()));
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
    public @NotNull String toString() {
        return "Factory {loc=%s, evt=%s}".formatted(this.getLocation(), this.soundEvent().getLocation());
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
                builder.attenuation,
                new MusicSettings(builder.musicMinDelay, builder.musicMaxDelay, builder.musicReplaceMusic));
    }

    public record MusicSettings(int minDelay, int maxDelay, boolean replaceCurrentMusic) {
        public static final MusicSettings DEFAULT = new MusicSettings(6000, 24000, false);

        public static final Codec<MusicSettings> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Codec.INT.optionalFieldOf("min_delay", DEFAULT.minDelay()).forGetter(MusicSettings::minDelay),
                        Codec.INT.optionalFieldOf("max_delay", DEFAULT.maxDelay()).forGetter(MusicSettings::maxDelay),
                        Codec.BOOL.optionalFieldOf("replace_current_music", DEFAULT.replaceCurrentMusic()).forGetter(MusicSettings::replaceCurrentMusic)
                ).apply(instance, MusicSettings::new));
    }
}
