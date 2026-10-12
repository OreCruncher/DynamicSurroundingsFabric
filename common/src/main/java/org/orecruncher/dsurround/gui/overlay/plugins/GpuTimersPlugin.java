package org.orecruncher.dsurround.gui.overlay.plugins;

import org.orecruncher.dsurround.effects.GpuTimer;
import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.di.Cacheable;

import java.util.List;

/**
 * How long the GPU takes to draw the mod's heavier effects (see {@link GpuTimer}), in a section of their own. Only
 * those drawn in the last second are shown.
 */
@Cacheable
public final class GpuTimersPlugin implements IDiagnosticPlugin {

    // In the order shown; a "- " name is part of the one before it
    private static final List<GpuTimer> TIMERS = List.of(
            AuroraRenderer.GPU_TIMER,
            PrecipitationShaders.GPU_TIMER,
            PrecipitationShaders.DAMP_SPOTS_TIMER,
            PrecipitationShaders.DROPS_TIMER,
            GroundFogVolume.GPU_TIMER,
            GroundFogVolume.MARCH_TIMER,
            GroundFogVolume.SMOOTH_TIMER,
            GroundFogVolume.COMPOSITE_TIMER,
            PointLights.GPU_TIMER);

    public GpuTimersPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    @Override
    public void onCollect(CollectDiagnosticsEvent event) {
        for (var timer : TIMERS)
            if (timer.isRecent())
                event.add(CollectDiagnosticsEvent.Section.GPU, timer);
    }
}
