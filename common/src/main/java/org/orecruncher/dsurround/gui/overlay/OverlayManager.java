package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.eventing.ClientState;

@Cacheable
public class OverlayManager {

    private final ObjectArray<AbstractOverlay> overlays;

    public OverlayManager() {
        this.overlays = new ObjectArray<>(3);
        this.overlays.add(ContainerManager.resolve(DiagnosticsOverlay.class));
        this.overlays.add(ContainerManager.resolve(CompassOverlay.class));
        this.overlays.add(ContainerManager.resolve(ClockOverlay.class));

        ClientState.TICK_END.register(this::tick);
    }

    public void render(GuiGraphics context, DeltaTracker deltaTracker) {
        var partialTick = deltaTracker.getGameTimeDeltaTicks();
        this.overlays.forEach(overlay -> overlay.render(context, partialTick));
    }

    public void tick(Minecraft client) {
        this.overlays.forEach(overlay -> overlay.tick(client));
    }

}
