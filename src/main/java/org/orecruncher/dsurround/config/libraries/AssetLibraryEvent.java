package org.orecruncher.dsurround.config.libraries;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

public class AssetLibraryEvent {

    private static final Configuration CONFIG = ContainerManager.resolve(Configuration.class);
    public static final IPhasedEvent<ReloadEvent> RELOAD = EventingFactory.createPrioritizedEvent();

    public static void reload() {
        RELOAD.raise(new ReloadEvent());
    }

    static {
        RELOAD.register(AssetLibraryEvent::afterReload, HandlerPriority.VERY_LOW);
    }

    private static void afterReload(ReloadEvent event) {
        // Only want to send a message if debug logging is enabled
        if (CONFIG.logging.enableDebugLogging) {
            var msg = Component.translatable("dsurround.text.reloadassets", Component.translatable("dsurround.modname"));
            var player = GameUtils.getPlayer();
            player.ifPresent( p -> p.sendSystemMessage(msg));
        }
    }

    public record ReloadEvent() {

    }
}
