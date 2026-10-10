package org.orecruncher.dsurround.lib.compat;

import net.minecraft.client.renderer.fog.FogData;
import org.joml.Vector4f;

/**
 * Copies and applies fog ranges. The game builds a {@link FogData} for each frame and the fog environments adjust it
 * in place, so the mod's changes take effect by writing them back into that object.
 */
public final class FogCompat {

    private FogCompat() {
    }

    /**
     * A copy of {@code data} with a new environmental range; everything else is kept.
     */
    public static FogData withRange(FogData data, float start, float end) {
        var result = new FogData();
        result.environmentalStart = start;
        result.renderDistanceStart = data.renderDistanceStart;
        result.environmentalEnd = end;
        result.renderDistanceEnd = data.renderDistanceEnd;
        result.skyEnd = data.skyEnd;
        result.cloudEnd = data.cloudEnd;
        result.color = data.color == null ? null : new Vector4f(data.color);
        return result;
    }

    /**
     * Sets the environmental range and the sky and cloud fog ends of {@code target}, the game's fog for this frame,
     * from {@code source}. The render distance fog is set by the game after the fog environments run, so it is left
     * alone.
     */
    public static void applyRange(FogData target, FogData source) {
        target.environmentalStart = source.environmentalStart;
        target.environmentalEnd = source.environmentalEnd;
        target.skyEnd = source.skyEnd;
        target.cloudEnd = source.cloudEnd;
    }
}
