package org.orecruncher.dsurround.runtime.diagnostics;

import org.orecruncher.dsurround.eventing.ClientEventHooks;

public interface IDiagnosticPlugin {

    void onCollect(ClientEventHooks.CollectDiagnosticsEvent event);
}
