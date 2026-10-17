package org.orecruncher.dsurround.gui.overlay.plugins;

import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.frost.FrostRenderer;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.effects.particles.Streaks;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.di.Cacheable;

import java.util.List;

/**
 * How long the GPU takes to draw the mod's heavier effects (see {@link RenderTimer}), in a section of their own. Only
 * those drawn in the last second are shown.
 */
@Cacheable
public final class RenderTimersPlugin implements IDiagnosticPlugin {

    // In the order shown; a "- " name is part of the one before it
    private static final List<RenderTimer> TIMERS = List.of(
            AuroraRenderer.RENDER_TIMER,
            PrecipitationShaders.RENDER_TIMER,
            PrecipitationShaders.DAMP_SPOTS_TIMER,
            PrecipitationShaders.DROPS_TIMER,
            GroundFogVolume.RENDER_TIMER,
            GroundFogVolume.MARCH_TIMER,
            GroundFogVolume.SMOOTH_TIMER,
            GroundFogVolume.COMPOSITE_TIMER,
            FrostRenderer.RENDER_TIMER,
            PointLights.RENDER_TIMER,
            Streaks.RENDER_TIMER);

    public RenderTimersPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    @Override
    public void onCollect(CollectDiagnosticsEvent event) {
        // The graphics mode: Fabulous's improved transparency draws translucent things, particles and weather to targets
        // of their own, laid over the scene by depth, which the mod's effects draw differently for
        var options = GameUtils.getGameSettings();
        boolean layered = GameUtils.getMC().gameRenderer.gameRenderState().useShaderTransparency();
        event.add(CollectDiagnosticsEvent.Section.Render, "Graphics: %s, improved transparency %s%s".formatted(
                options.graphicsPreset().get(), options.improvedTransparency().get() ? "on" : "off",
                layered ? " (layered by depth)" : ""));
        boolean labelled = false;
        for (var timer : TIMERS) {
            if (!timer.isRecent())
                continue;
            if (!labelled) {
                event.add(CollectDiagnosticsEvent.Section.Render, "ms per frame: CPU avg, peak / GPU avg, peak");
                labelled = true;
            }
            event.add(CollectDiagnosticsEvent.Section.Render, timer);
        }
    }
}
