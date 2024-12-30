package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;

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

    protected float getRenderDistance() {
        float h = GameUtils.getMC().gameRenderer.getRenderDistance();
        return Math.max(h, 32);
    }

    public void tick() {

    }

    public void disconnect() {

    }
}
