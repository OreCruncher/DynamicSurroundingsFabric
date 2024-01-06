package org.orecruncher.dsurround.gui.hud;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.ClientEventHooks;

public interface IDiagnosticPlugin {

    default void tick(Minecraft client) {
        // By default, does nothing.  Implement if the plugin needs to be ticked
    }

    void onCollect(ClientEventHooks.CollectDiagnosticsEvent event);
}
