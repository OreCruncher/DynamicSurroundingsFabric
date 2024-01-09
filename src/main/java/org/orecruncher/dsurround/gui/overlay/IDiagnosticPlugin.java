package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;

public interface IDiagnosticPlugin {

    default void tick(Minecraft client) {
        // By default, does nothing.  Implement if the plugin needs to be ticked
    }

    void onCollect(CollectDiagnosticsEvent event);
}
