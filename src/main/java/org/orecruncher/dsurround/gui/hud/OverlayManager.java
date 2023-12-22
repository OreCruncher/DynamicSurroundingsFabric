package org.orecruncher.dsurround.gui.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.infra.events.ClientState;

@Environment(EnvType.CLIENT)
public class OverlayManager {

    private final ObjectArray<AbstractHudOverlay> overlays;

    public OverlayManager(Configuration config) {
        this.overlays = new ObjectArray<>();
        this.overlays.add(new DiagnosticsHud());
        this.overlays.add(new CompassHud(config));
        ClientState.TICK_END.register(this::tick);
    }

    public void render(DrawContext context) {
        this.overlays.forEach(overlay -> overlay.render(context));
    }

    public void tick(MinecraftClient client) {
        this.overlays.forEach(overlay -> overlay.tick(client));
    }

}
