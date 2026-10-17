package org.orecruncher.dsurround.runtime.weather;

/**
 * How hard it is raining or snowing, in bands of the rain level. A band covers the levels above the band before it,
 * up to and including its own.
 */
public enum StormIntensity {
    NONE(0F),
    CALM(0.125F),
    LIGHT(0.25F),
    GENTLE(0.375F),
    MODERATE(0.5F),
    HEAVY(0.625F),
    STRONG(0.75F),
    INTENSE(0.875F),
    TORRENTIAL(1F);

    private static final StormIntensity[] VALUES = values();

    private final float level;

    StormIntensity(float level) {
        this.level = level;
    }

    /**
     * The highest rain level in this band
     */
    public float level() {
        return this.level;
    }

    /**
     * The band a rain level falls in. Levels above 1 are torrential.
     */
    public static StormIntensity of(float rainLevel) {
        for (var band : VALUES)
            if (rainLevel <= band.level)
                return band;
        return TORRENTIAL;
    }
}
