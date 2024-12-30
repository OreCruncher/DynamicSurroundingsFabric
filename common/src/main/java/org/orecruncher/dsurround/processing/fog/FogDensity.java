package org.orecruncher.dsurround.processing.fog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.orecruncher.dsurround.config.SoundEventType;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum FogDensity {

    NONE("none", 0F,0F, 0F, 0F),
    LIGHT("light", 0.3F, Constants.START + 12.5F, Constants.END - 12.5F, Constants.RESERVE + 5F),
    NORMAL("normal", 0.47F, Constants.START, Constants.END, Constants.RESERVE),
    MEDIUM("medium", 0.64F, Constants.START - 12.5F, Constants.END + 12.5F, Constants.RESERVE),
    HEAVY("heavy", 0.8F, Constants.START - 12.5F, Constants.END + 25F, Constants.RESERVE - 5F);

    private final String name;
    private final float start;
    private final float end;
    private final float reserve;
    private final float intensity;

    FogDensity(final String name, final float intensity, final float start, final float end, final float reserve) {
        this.name = name;
        this.intensity = intensity;
        this.start = start;
        this.end = end;
        this.reserve = reserve;
    }

    private static final Map<String, FogDensity> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(FogDensity::getName, (category) -> category));
    public static final Codec<FogDensity> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown sound event type"), FogDensity::getName);

    public boolean inRange(float celestialAngleDegrees) {
        return celestialAngleDegrees >= this.start && celestialAngleDegrees <= this.end;
    }

    public String getName() {
        return this.name;
    }

    public float getIntensity() {
        return this.intensity;
    }

    public float getStartAngle() {
        return this.start;
    }

    public float getEndAngle() {
        return this.end;
    }

    public float getReserve() {
        return this.reserve;
    }

    private static class Constants {
        public static final float START = 270F;
        public static final float END = 317F;
        public static final float RESERVE = 10F;
    }
}