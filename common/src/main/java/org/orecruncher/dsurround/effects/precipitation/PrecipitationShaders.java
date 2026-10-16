package org.orecruncher.dsurround.effects.precipitation;

import org.orecruncher.dsurround.effects.RenderTimer;

/**
 * The shaders particle rain and snow are drawn with. Both fade what they draw into the fog. Kept apart from
 * {@link PrecipitationField} and {@link DampSpots}, so the drops and spots can be tested without the game.
 * <p>
 * 26.2 port: the drops' and damp spots' pipelines, and the hook that draws them in place of vanilla's weather, aren't
 * built yet (0.4.7's are the reference), so particle weather stands aside and vanilla's is drawn.
 */
public final class PrecipitationShaders {

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Precipitation");
    public static final RenderTimer DAMP_SPOTS_TIMER = new RenderTimer("- Damp Spots");
    public static final RenderTimer DROPS_TIMER = new RenderTimer("- Drops");

    private PrecipitationShaders() {
    }

    /**
     * Whether the drops can be drawn. Not yet on 26.2.
     */
    public static boolean isAvailable() {
        return false;
    }
}
