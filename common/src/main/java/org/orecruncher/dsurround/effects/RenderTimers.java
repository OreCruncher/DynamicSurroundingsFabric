package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.aurora.RealisticAuroraRenderer;
import org.orecruncher.dsurround.effects.frost.FrostRenderer;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;
import org.orecruncher.dsurround.effects.lightning.LightningRenderer;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.Streaks;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;

import java.util.List;

/**
 * The mod's render timers (see {@link RenderTimer}): how long its heavier effects take to draw. The diagnostics overlay
 * shows them, and a profiling recording (/dsprofile) writes them into the recording.
 */
public final class RenderTimers {

    /**
     * All of them, in the order shown; a "- " name is part of the one before it
     */
    public static final List<RenderTimer> ALL = List.of(
            AuroraRenderer.RENDER_TIMER,
            RealisticAuroraRenderer.RENDER_TIMER,
            PrecipitationShaders.RENDER_TIMER,
            PrecipitationShaders.DAMP_SPOTS_TIMER,
            PrecipitationShaders.DROPS_TIMER,
            GroundFogVolume.RENDER_TIMER,
            GroundFogVolume.MARCH_TIMER,
            GroundFogVolume.SMOOTH_TIMER,
            GroundFogVolume.COMPOSITE_TIMER,
            FrostRenderer.RENDER_TIMER,
            LightningRenderer.RENDER_TIMER,
            PointLights.RENDER_TIMER,
            Streaks.RENDER_TIMER);

    private RenderTimers() {
    }
}
