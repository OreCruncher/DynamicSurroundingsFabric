package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class DimensionConfig {

    public static Codec<DimensionConfig> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Identifier.CODEC.fieldOf("dimId").forGetter(info -> info.dimensionId),
            Codec.INT.optionalFieldOf("seaLevel").forGetter(info -> info.seaLevel),
            Codec.INT.optionalFieldOf("skyHeight").forGetter(info -> info.skyHeight),
            Codec.INT.optionalFieldOf("cloudHeight").forGetter(info -> info.cloudHeight),
            Codec.BOOL.optionalFieldOf("alwaysOutside").forGetter(info -> info.alwaysOutside),
            Codec.BOOL.optionalFieldOf("playBiomeSounds").forGetter(info -> info.playBiomeSounds))
            .apply(instance, DimensionConfig::new));

    public Identifier dimensionId;
    public Optional<Integer> seaLevel;
    public Optional<Integer> skyHeight;
    public Optional<Integer> cloudHeight;
    public Optional<Boolean> alwaysOutside;
    public Optional<Boolean> playBiomeSounds;

    DimensionConfig(Identifier dimensionId, Optional<Integer> seaLevel, Optional<Integer> skyHeight,
            Optional<Integer> cloudHeight, Optional<Boolean> alwaysOutside, Optional<Boolean> playBiomeSounds) {
        this.dimensionId = dimensionId;
        this.seaLevel = seaLevel;
        this.skyHeight = skyHeight;
        this.cloudHeight = cloudHeight;
        this.alwaysOutside = alwaysOutside;
        this.playBiomeSounds = playBiomeSounds;
    }

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
        return this.dimensionId != null ? this.dimensionId.hashCode() : 0;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final DimensionConfig dc) {
            return this.dimensionId.equals(dc.dimensionId);
        }
        return false;
    }
}
