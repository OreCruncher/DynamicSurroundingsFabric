package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

@Cacheable
public class OverlayManager {

    private final ObjectArray<AbstractOverlay> overlays;

    public OverlayManager() {
        this.overlays = new ObjectArray<>(2);
        this.overlays.add(ContainerManager.resolve(DiagnosticsOverlay.class));
        this.overlays.add(ContainerManager.resolve(CompassAndClockOverlay.class));

        ClientState.TICK_END.register(this::tick);
    }

    public void render(GuiGraphics context, float partialTick) {
        this.overlays.forEach(overlay -> overlay.render(context, partialTick));
    }

    public void tick(Minecraft client) {
        this.overlays.forEach(overlay -> overlay.tick(client));
    }

}
