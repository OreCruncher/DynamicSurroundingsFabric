package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.compat.FogCompat;

public abstract class VanillaFogRangeCalculator implements IFogRangeCalculator {

    protected final Configuration.FogOptions fogOptions;
    private final String name;

    protected VanillaFogRangeCalculator(@NotNull final String name, Configuration.FogOptions fogOptions) {
        this.name = name;
        this.fogOptions = fogOptions;
    }

    @NotNull
    public String getName() {
        return this.name;
    }

    public abstract boolean enabled();

    @NotNull
    public FogData render(@NotNull final FogData data, float renderDistance, float partialTick) {
        return data;
    }

    /**
     * A copy of {@code data} with a new environmental range; everything else is kept.
     */
    @NotNull
    protected static FogData withRange(@NotNull final FogData data, float start, float end) {
        return FogCompat.withRange(data, start, end);
    }

    // The fog start, as a fraction of the end, before intensity shrinks it further
    static final float CLEAR_ZONE = 0.9F;
    // Intensity at which the target replaces the game's fog completely; below it the two are blended
    static final float FULL_EFFECT_INTENSITY = 0.25F;
    // Closest the fog end is allowed to come, in blocks
    static final float MIN_END = 4F;

    /**
     * A copy of {@code data} with thicker fog. The game's environmental fog is a faint haze from 0 to about 1024
     * blocks, independent of render distance, so it can't be scaled the way the old terrain fog could. Instead the
     * target range is worked out from what the player can see now (render distance, or nearer when the game's own fog,
     * like rain or the Nether, ends closer), the same way the old terrain fog behaved, and blended in from the game's
     * fog as the intensity rises. An intensity of 0 leaves the game's fog unchanged, and the result is never thinner
     * than it. The sky and clouds fade along with the terrain so they don't stay clear behind thick fog.
     *
     * @param renderDistance The render distance in blocks
     * @param intensity      0 for none, 1 for the thickest
     * @param minStart       The nearest the fog start may be at full effect, in blocks
     */
    @NotNull
    static FogData thicken(@NotNull final FogData data, float renderDistance, float intensity, float minStart) {
        if (intensity <= 0F)
            return data;

        var i = Math.min(intensity, 1F);
        var visible = Math.min(renderDistance, data.environmentalEnd);
        var targetEnd = Math.max(visible * (1F - i), MIN_END);
        var targetStart = Math.min(Math.max(targetEnd * (1F - i) * CLEAR_ZONE, minStart), targetEnd);

        var weight = Math.min(i / FULL_EFFECT_INTENSITY, 1F);
        var end = Math.min(blendDistance(weight, data.environmentalEnd, targetEnd), data.environmentalEnd);
        var start = Math.min(Mth.lerp(weight, data.environmentalStart, targetStart), end);

        var result = withRange(data, start, end);
        result.skyEnd = Math.min(blendDistance(weight, data.skyEnd, targetEnd), data.skyEnd);
        result.cloudEnd = Math.min(blendDistance(weight, data.cloudEnd, targetEnd), data.cloudEnd);
        return result;
    }

    /**
     * Blends two fog distances by their reciprocal, the fog's density. Blending the distances directly would hold the
     * fog thin until the weight is nearly 1 and then close in all at once, since the game's distances are far larger
     * than the target's.
     */
    private static float blendDistance(float weight, float from, float to) {
        if (from <= 0F || to <= 0F)
            return Math.min(from, to);
        return 1F / Mth.lerp(weight, 1F / from, 1F / to);
    }

    public void tick() {

    }

    public void disconnect() {

    }
}
