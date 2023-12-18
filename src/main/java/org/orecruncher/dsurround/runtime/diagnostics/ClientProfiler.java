package org.orecruncher.dsurround.runtime.diagnostics;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

@Environment(EnvType.CLIENT)
public final class ClientProfiler {

    private static final TimerEMA clientTick = new TimerEMA("Client Tick");
    private static final TimerEMA lastTick = new TimerEMA("Last Tick");
    private static long lastTickMark = -1;
    private static long timeMark = 0;
    private static float tps = 0;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientProfiler::tickStart);
        ClientTickEvents.END_CLIENT_TICK.register(ClientProfiler::tickEnd);
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(ClientProfiler::onCollect, HandlerPriority.VERY_HIGH);
    }

    private static void tickStart(MinecraftClient client) {
        timeMark = System.nanoTime();
        if (lastTickMark != -1) {
            lastTick.update(timeMark - lastTickMark);
            tps = MathHelper.clamp((float) (50F / lastTick.getMSecs() * 20F), 0F, 20F);
        }
        lastTickMark = timeMark;
    }

    private static void tickEnd(MinecraftClient client) {
        final long delta = System.nanoTime() - timeMark;
        clientTick.update(delta);
    }

    private static void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
        var tpsTimer = new ITimer() {
            @Override
            public double getMSecs() {
                return tps;
            }

            @Override
            public String toString() {
                return String.format("Client TPS:%7.3fms", this.getMSecs());
            }
        };

        event.timers.add(tpsTimer);
        event.timers.add(clientTick);
        event.timers.add(lastTick);
    }
}
