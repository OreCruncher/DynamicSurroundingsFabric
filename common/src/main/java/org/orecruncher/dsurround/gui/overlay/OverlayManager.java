package org.orecruncher.dsurround.gui.overlay;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.eventing.ClientState;

import java.util.List;

@Cacheable
public final class OverlayManager {

    private final List<AbstractOverlay> overlays;

    public OverlayManager() {
        this.overlays = ImmutableList.of(
                ContainerManager.resolve(DiagnosticsOverlay.class),
                ContainerManager.resolve(CompassOverlay.class),
                ContainerManager.resolve(ClockOverlay.class)
        );

        ClientState.CLIENT_TICK_END_EVENT.register(this::tick);
    }

    public void render(GuiGraphics context, DeltaTracker deltaTracker) {
        var partialTick = deltaTracker.getGameTimeDeltaTicks();
        this.overlays.forEach(overlay -> overlay.render(context, partialTick));
    }

    public void tick(Minecraft client) {
        this.overlays.forEach(overlay -> overlay.tick(client));
    }

}
