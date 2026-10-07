package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;

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
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {
        return data;
    }

    /**
     * A copy of {@code data} with a new range; the mode and shape are kept.
     */
    @NotNull
    protected static FogRenderer.FogData withRange(@NotNull final FogRenderer.FogData data, float start, float end) {
        var result = new FogRenderer.FogData(data.mode);
        result.shape = data.shape;
        result.start = start;
        result.end = end;
        return result;
    }

    // Closest the fog end is allowed to come, in blocks
    static final float MIN_END = 4F;

    /**
     * A copy of {@code data} with thicker fog. The game's fog ends at render distance, with terrain fog starting about
     * 90% of the way there and sky fog starting at the camera, so both are pulled in from their own range: the end
     * scales with {@code 1 - intensity} and the start with its square, opening the gap as the fog thickens. An
     * intensity of 0 leaves the game's fog unchanged, and the result is never thinner than it.
     *
     * @param intensity 0 for none, 1 for the thickest
     * @param minStart  The nearest the fog start may come, in blocks; never further than the game's own start
     */
    @NotNull
    static FogRenderer.FogData thicken(@NotNull final FogRenderer.FogData data, float intensity, float minStart) {
        if (intensity <= 0F)
            return data;

        var scale = 1F - Math.min(intensity, 1F);
        var end = Math.min(Math.max(data.end * scale, MIN_END), data.end);
        var start = Math.max(data.start * scale * scale, Math.min(minStart, data.start));
        return withRange(data, Math.min(start, end), end);
    }

    public void tick() {

    }

    public void disconnect() {

    }
}
