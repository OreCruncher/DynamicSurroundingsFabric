package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;

public interface IFogRangeCalculator {

    /**
     * The name of the fog calculator for logging purposes.
     *
     * @return The name of the fog calculator
     */
    @NotNull
    String getName();

    /**
     * If the calculator is enabled or not
     * @return true if enabled, false otherwise
     */
    boolean enabled();

    /**
     * Called during the render pass to get parameters for fog rendering.
     *
     * @param data Fog data instance to update
     * @return The fog data instance passed in
     */
    @NotNull
    FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick);

    /**
     * Called once every client side tick. Up to the calculator to figure out what
     * to do with the time, if anything.
     */
    void tick();

    /**
     * Called when the client disconnects from the server
     */
    void disconnect();
}
