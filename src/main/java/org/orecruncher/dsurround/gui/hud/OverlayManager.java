package org.orecruncher.dsurround.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

public class OverlayManager {

    private final ObjectArray<AbstractOverlay> overlays;

    public OverlayManager(Configuration config, ITagLibrary tagLibrary) {
        this.overlays = new ObjectArray<>();
        this.overlays.add(ContainerManager.resolve(DiagnosticsOverlay.class));
        this.overlays.add(new CompassAndClockOverlay(config, tagLibrary));
        ClientState.TICK_END.register(this::tick);
    }

    public void render(GuiGraphics context) {
        this.overlays.forEach(overlay -> overlay.render(context));
    }

    public void tick(Minecraft client) {
        this.overlays.forEach(overlay -> overlay.tick(client));
    }

}
