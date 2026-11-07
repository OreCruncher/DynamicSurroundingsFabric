// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.overlay.plugins;

import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.Cacheable;

/**
 * How long the GPU takes to draw the mod's heavier effects (see {@link RenderTimer}), in a section of their own. Only
 * those drawn in the last second are shown.
 */
@Cacheable
public final class RenderTimersPlugin implements IDiagnosticPlugin {

    public RenderTimersPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    @Override
    public void onCollect(CollectDiagnosticsEvent event) {
        var options = GameUtils.getGameSettings();
        event.add(CollectDiagnosticsEvent.Section.Render, "Graphics: %s, improved transparency %s".formatted(
                options.graphicsPreset().get(), options.improvedTransparency().get() ? "on" : "off"));
        boolean labelled = false;
        for (var timer : RenderTimers.ALL) {
            if (timer.isStale())
                continue;
            if (!labelled) {
                event.add(CollectDiagnosticsEvent.Section.Render, "ms per frame: CPU avg, peak / GPU avg, peak");
                labelled = true;
            }
            event.add(CollectDiagnosticsEvent.Section.Render, timer);
        }
    }
}
