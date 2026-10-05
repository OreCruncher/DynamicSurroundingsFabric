package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
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

    public void tick() {

    }

    public void disconnect() {

    }
}
