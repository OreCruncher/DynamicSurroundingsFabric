package org.orecruncher.dsurround.sound;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import org.orecruncher.dsurround.lib.registry.IdentityUtils;
import org.orecruncher.dsurround.lib.Library;

/**
 * Codecs for the sound settings in the mod's configuration files.
 */
public final class SoundCodecHelpers {

    /**
     * A sound category by name, case-insensitive. An unknown name is logged and treated as AMBIENT.
     */
    public static final Codec<SoundSource> SOUND_CATEGORY_CODEC = Codec.STRING.xmap(SoundCodecHelpers::lookupSoundSource, SoundSource::getName);

    /**
     * An attenuation by name (none or linear), case-insensitive. An unknown name is logged and treated as LINEAR.
     */
    public static final Codec<SoundInstance.Attenuation> ATTENUATION_CODEC = Codec.STRING.xmap(SoundCodecHelpers::lookupAttenuation, SoundInstance.Attenuation::name);

    /**
     * A sound event: either just its ID (variable range), or a full definition.
     */
    public static final Codec<SoundEvent> SOUND_EVENT_CODEC = Codec.either(IdentityUtils.CODEC, SoundEvent.DIRECT_CODEC)
            .xmap(either -> either.map(SoundEvent::createVariableRangeEvent, x -> x), Either::right);

    /**
     * A volume or pitch: a number for a fixed value, or {@code {"min": a, "max": b}} for a random value in that
     * range (each defaults to 1.0). A range whose ends are equal is a fixed value; one with min above max is an
     * error.
     */
    public static final Codec<FloatProvider> SOUND_PROPERTY_RANGE = Codec.either(Codec.FLOAT, RangeProperty.CODEC)
            .flatXmap(SoundCodecHelpers::toFloatProvider, SoundCodecHelpers::fromFloatProvider);

    public record RangeProperty(float min, float max) {
        static final Codec<RangeProperty> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Codec.FLOAT.optionalFieldOf("min", 1.0F).forGetter(RangeProperty::min),
                        Codec.FLOAT.optionalFieldOf("max", 1.0F).forGetter(RangeProperty::max)
                ).apply(instance, RangeProperty::new));
    }

    private SoundCodecHelpers() {
    }

    private static DataResult<FloatProvider> toFloatProvider(Either<Float, RangeProperty> either) {
        return either.map(
                value -> DataResult.success(ConstantFloat.of(value)),
                range -> {
                    if (range.min() == range.max())
                        return DataResult.success(ConstantFloat.of(range.min()));
                    if (range.min() > range.max())
                        return DataResult.error(() -> "Range min (%s) is greater than max (%s)".formatted(range.min(), range.max()));
                    return DataResult.success(UniformFloat.of(range.min(), range.max()));
                });
    }

    private static DataResult<Either<Float, RangeProperty>> fromFloatProvider(FloatProvider provider) {
        if (provider instanceof ConstantFloat constant)
            return DataResult.success(Either.left(constant.value()));
        if (provider instanceof UniformFloat uniform)
            return DataResult.success(Either.right(new RangeProperty(uniform.min(), uniform.max())));
        return DataResult.error(() -> "Only fixed values and uniform ranges can be written: " + provider);
    }

    private static SoundSource lookupSoundSource(String string) {
        for (var c : SoundSource.values())
            if (c.getName().equalsIgnoreCase(string))
                return c;
        Library.LOGGER.warn("Unknown sound category '%s'; using %s", string, SoundSource.AMBIENT.getName());
        return SoundSource.AMBIENT;
    }

    private static SoundInstance.Attenuation lookupAttenuation(String string) {
        for (var c : SoundInstance.Attenuation.values())
            if (c.name().equalsIgnoreCase(string))
                return c;
        Library.LOGGER.warn("Unknown sound attenuation '%s'; using %s", string, SoundInstance.Attenuation.LINEAR.name());
        return SoundInstance.Attenuation.LINEAR;
    }
}
