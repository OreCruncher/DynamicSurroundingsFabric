package org.orecruncher.dsurround.eventing.handlers;

import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.lib.math.TimerEMA;

@Environment(EnvType.CLIENT)
public final class DiagnosticHandler {

    private static final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Diagnostics");
    private static boolean enableCollection = false;
    private static ObjectArray<String> left = new ObjectArray<>();
    private static ObjectArray<String> right = new ObjectArray<>();

    static {
        ClientState.TICK_END.register(DiagnosticHandler::tick);
    }

    public static void toggleCollection() {
        enableCollection = !enableCollection;
    }

    public static boolean isCollecting() {
        return enableCollection;
    }

    /**
     * Called by a mixin hook to obtain information for rendering in the diagnostic HUD
     */
    public static ObjectArray<String> getLeft() {
        return left;
    }

    /**
     * Called by a mixin hook to obtain information for rendering in the diagnostic HUD
     */
    public static ObjectArray<String> getRight() {
        return right;
    }

    private static void tick(MinecraftClient client) {
        if (enableCollection && GameUtils.isInGame()) {
            diagnostics.begin();

            var event = new ClientEventHooks.CollectDiagnosticsEvent();

            event.left.add(Client.Branding);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise(event);

            event.timers.add(diagnostics);

            left = event.left;
            right = new ObjectArray<>(event.right.size() + event.timers.size() + 1);

            if (!event.timers.isEmpty()) {
                for (var timer : event.timers) {
                    right.add(Formatting.LIGHT_PURPLE + timer.toString());
                }
            }

            right.add(Strings.EMPTY);
            right.addAll(event.right);

            diagnostics.end();
        }
    }
}
