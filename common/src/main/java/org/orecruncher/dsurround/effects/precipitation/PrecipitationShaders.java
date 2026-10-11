package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

/**
 * The shaders particle rain and snow are drawn with. Both fade what they draw into the fog. Kept apart from
 * {@link PrecipitationField} and {@link DampSpots}, so the drops and spots can be tested without the game.
 */
public final class PrecipitationShaders {

    /**
     * Draws the drops, lit by the lightmap: rain plain, snowflakes textured. Without it, vanilla's weather is drawn
     * instead.
     */
    public static final ModShader DROPS = new ModShader("precipitation", DefaultVertexFormat.PARTICLE,
            "particle rain and snow", "vanilla's rain and snow will be drawn instead", Library.LOGGER);

    /**
     * Draws the damp spots rain leaves on soft ground
     */
    public static final ModShader DAMP_SPOTS = new ModShader("damp_spot", DefaultVertexFormat.POSITION_TEX_COLOR,
            "damp spots", "rain will not leave damp spots", Library.LOGGER);

    private PrecipitationShaders() {
    }
}
