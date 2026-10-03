package org.orecruncher.dsurround.lib.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogRenderer;

/**
 * Reads and sets the fog the shaders use. Later Minecraft versions rebuild the fog pipeline, so this keeps the
 * render system calls in one place.
 */
public final class FogCompat {

    private FogCompat() {
    }

    /**
     * The fog range and shape currently configured for the shaders, in a new {@link FogRenderer.FogData} for
     * {@code mode}.
     */
    public static FogRenderer.FogData currentShaderFog(FogRenderer.FogMode mode) {
        var data = new FogRenderer.FogData(mode);
        data.start = RenderSystem.getShaderFogStart();
        data.end = RenderSystem.getShaderFogEnd();
        data.shape = RenderSystem.getShaderFogShape();
        return data;
    }

    /**
     * Sets the shaders' fog range and shape from {@code data}.
     */
    public static void applyShaderFog(FogRenderer.FogData data) {
        RenderSystem.setShaderFogStart(data.start);
        RenderSystem.setShaderFogEnd(data.end);
        RenderSystem.setShaderFogShape(data.shape);
    }
}
