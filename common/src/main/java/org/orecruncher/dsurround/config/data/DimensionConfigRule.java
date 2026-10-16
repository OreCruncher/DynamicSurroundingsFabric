package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record DimensionConfigRule(
        Identifier dimensionId,
        Optional<Integer> seaLevel,
        Optional<Integer> skyHeight,
        Optional<Integer> cloudHeight,
        Optional<Boolean> alwaysOutside,
        Optional<Boolean> playBiomeSounds,
        Optional<Boolean> compassWobble,
        Optional<Boolean> particleWeather,
        Optional<Boolean> simulateWind,
        Optional<Boolean> groundFog) {

    public static final Codec<DimensionConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                    Identifier.CODEC.fieldOf("dimId").forGetter(DimensionConfigRule::dimensionId),
                Codec.INT.optionalFieldOf("seaLevel").forGetter(DimensionConfigRule::seaLevel),
                Codec.INT.optionalFieldOf("skyHeight").forGetter(DimensionConfigRule::skyHeight),
                Codec.INT.optionalFieldOf("cloudHeight").forGetter(DimensionConfigRule::cloudHeight),
                Codec.BOOL.optionalFieldOf("alwaysOutside").forGetter(DimensionConfigRule::alwaysOutside),
                Codec.BOOL.optionalFieldOf("playBiomeSounds").forGetter(DimensionConfigRule::playBiomeSounds),
                Codec.BOOL.optionalFieldOf("compassWobble").forGetter(DimensionConfigRule::compassWobble),
                Codec.BOOL.optionalFieldOf("particleWeather").forGetter(DimensionConfigRule::particleWeather),
                Codec.BOOL.optionalFieldOf("simulateWind").forGetter(DimensionConfigRule::simulateWind),
                Codec.BOOL.optionalFieldOf("groundFog").forGetter(DimensionConfigRule::groundFog))
                .apply(instance, DimensionConfigRule::new));

    @Override
    public @NotNull String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("dimensionId: ").append(this.dimensionId);
        this.seaLevel.ifPresent(v -> builder.append(" seaLevel: ").append(v));
        this.skyHeight.ifPresent(v -> builder.append(" skyHeight: ").append(v));
        this.cloudHeight.ifPresent(v -> builder.append(" cloudHeight: ").append(v));
        this.alwaysOutside.ifPresent(v -> builder.append(" alwaysOutside: ").append(v));
        this.playBiomeSounds.ifPresent(v -> builder.append(" playBiomeSounds: ").append(v));
        this.compassWobble.ifPresent(v -> builder.append(" compassWobble: ").append(v));
        this.particleWeather.ifPresent(v -> builder.append(" particleWeather: ").append(v));
        this.simulateWind.ifPresent(v -> builder.append(" simulateWind: ").append(v));
        this.groundFog.ifPresent(v -> builder.append(" groundFog: ").append(v));
        return builder.toString();
    }

}
