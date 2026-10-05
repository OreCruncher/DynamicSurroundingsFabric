package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;

/**
 * Adds lines to the diagnostics overlay. Implementations register their {@code onCollect} with
 * {@code ICollectDiagnostics.EVENT} in their constructor, so each must be created once: mark the class
 * {@code @Cacheable} (the annotation is not inherited from an interface).
 */
public interface IDiagnosticPlugin {

    default void tick(Minecraft client) {
        // By default, does nothing.  Implement if the plugin needs to be ticked
    }

    void onCollect(CollectDiagnosticsEvent event);
}
