package org.orecruncher.dsurround.config.libraries;

import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

public class AssetLibraryEvent {

    public static final IPhasedEvent<ReloadEvent> RELOAD = EventingFactory.createPrioritizedEvent();

    public static void reload() {
        RELOAD.raise(new ReloadEvent());
    }

    public record ReloadEvent() {

    }
}
