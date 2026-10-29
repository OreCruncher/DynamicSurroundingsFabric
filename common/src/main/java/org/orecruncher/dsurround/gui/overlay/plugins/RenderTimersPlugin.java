package org.orecruncher.dsurround.gui.overlay.plugins;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.lib.GameUtils;
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

    public RenderTimersPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    @Override
    public void onCollect(CollectDiagnosticsEvent event) {
        // The graphics mode: Fabulous's improved transparency draws translucent things, particles and weather to targets
        // of their own, laid over the scene by depth, which the mod's effects draw differently for
        var options = GameUtils.getGameSettings();
        boolean layered = Minecraft.useShaderTransparency();
        event.add(CollectDiagnosticsEvent.Section.Render, "Graphics: %s, improved transparency %s%s".formatted(
                options.graphicsPreset().get(), options.improvedTransparency().get() ? "on" : "off",
                layered ? " (layered by depth)" : ""));
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
