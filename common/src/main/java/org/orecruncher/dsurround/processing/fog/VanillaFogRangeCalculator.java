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

    public void tick() {

    }

    public void disconnect() {

    }
}
