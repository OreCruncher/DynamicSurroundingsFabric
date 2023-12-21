package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public record DimensionConfigRule(
        Identifier dimensionId,
        Optional<Integer> seaLevel,
        Optional<Integer> skyHeight,
        Optional<Integer> cloudHeight,
        Optional<Boolean> alwaysOutside,
        Optional<Boolean> playBiomeSounds) {

    public static Codec<DimensionConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Identifier.CODEC.fieldOf("dimId").forGetter(DimensionConfigRule::dimensionId),
            Codec.INT.optionalFieldOf("seaLevel").forGetter(DimensionConfigRule::seaLevel),
            Codec.INT.optionalFieldOf("skyHeight").forGetter(DimensionConfigRule::skyHeight),
            Codec.INT.optionalFieldOf("cloudHeight").forGetter(DimensionConfigRule::cloudHeight),
            Codec.BOOL.optionalFieldOf("alwaysOutside").forGetter(DimensionConfigRule::alwaysOutside),
            Codec.BOOL.optionalFieldOf("playBiomeSounds").forGetter(DimensionConfigRule::playBiomeSounds))
            .apply(instance, DimensionConfigRule::new));

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("dimensionId: ").append(this.dimensionId);
        this.seaLevel.ifPresent(v -> builder.append(" seaLevel: ").append(v));
        this.skyHeight.ifPresent(v -> builder.append(" seaLevel: ").append(v));
        this.cloudHeight.ifPresent(v -> builder.append(" cloudHeight: ").append(v));
        this.alwaysOutside.ifPresent(v -> builder.append(" alwaysOutside: ").append(v));
        this.playBiomeSounds.ifPresent(v -> builder.append(" playBiomeSounds: ").append(v));
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return this.dimensionId.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final DimensionConfigRule dc) {
            return this.dimensionId.equals(dc.dimensionId);
        }
        return false;
    }
}
