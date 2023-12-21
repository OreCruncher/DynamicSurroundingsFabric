package org.orecruncher.dsurround.config.libraries;

import net.minecraft.text.Text;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

public class AssetLibraryEvent {

    public static final IPhasedEvent<ReloadEvent> RELOAD = EventingFactory.createPrioritizedEvent();

    public static void reload() {
        RELOAD.raise(new ReloadEvent());
    }

    static {
        RELOAD.register(AssetLibraryEvent::afterReload, HandlerPriority.VERY_LOW);
    }

    private static void afterReload(ReloadEvent event) {
        // Only want to send a message if debug logging is enabled
        if (Client.Config.logging.enableDebugLogging) {
            var player = GameUtils.getPlayer();
            if (player != null) {
                var msg = Text.stringifiedTranslatable("dsurround.text.reloadassets", Text.translatable("dsurround.modname"));
                player.sendMessage(msg);
            }
        }
    }

    public record ReloadEvent() {

    }
}
