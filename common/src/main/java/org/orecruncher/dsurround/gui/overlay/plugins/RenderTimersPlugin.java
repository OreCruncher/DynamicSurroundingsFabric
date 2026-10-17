package org.orecruncher.dsurround.gui.overlay.plugins;

import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.di.Cacheable;

/**
 * How long the GPU takes to draw the mod's heavier effects (see {@link RenderTimer}), in a section of their own. Only
 * those drawn in the last second are shown.
 */
@Cacheable
public final class RenderTimersPlugin implements IDiagnosticPlugin {

    // In the order shown; a "- " name is part of the one before it

    public RenderTimersPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    @Override
    public void onCollect(CollectDiagnosticsEvent event) {
        boolean labelled = false;
        for (var timer : RenderTimers.ALL) {
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
