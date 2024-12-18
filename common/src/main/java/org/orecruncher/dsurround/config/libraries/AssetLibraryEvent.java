package org.orecruncher.dsurround.config.libraries;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

public class AssetLibraryEvent {

    private static final Configuration CONFIG = ContainerManager.resolve(Configuration.class);
    public static final IPhasedEvent<IReloadEvent> RELOAD = EventingFactory.createPrioritizedEvent(callbacks -> (resourceUtilities, scope) ->  {
        for (var callback : callbacks) {
            callback.onReload(resourceUtilities, scope);
        }
    });

    static {
        RELOAD.register(AssetLibraryEvent::afterReload, HandlerPriority.VERY_LOW);
    }

    private static void afterReload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        // Only want to send a message if debug logging is enabled
        if (CONFIG.logging.enableDebugLogging) {
            var msg = Component.translatable("dsurround.text.reloadassets", Component.translatable("dsurround.modname"));
            GameUtils.getPlayer().ifPresent(p -> p.sendSystemMessage(msg));
        }
    }

}
