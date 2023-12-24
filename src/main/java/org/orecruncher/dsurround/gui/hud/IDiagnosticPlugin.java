package org.orecruncher.dsurround.gui.hud;

import org.orecruncher.dsurround.eventing.ClientEventHooks;

public interface IDiagnosticPlugin {

    void onCollect(ClientEventHooks.CollectDiagnosticsEvent event);
}
