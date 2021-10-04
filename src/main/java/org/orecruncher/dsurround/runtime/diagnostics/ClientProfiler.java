package org.orecruncher.dsurround.runtime.diagnostics;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.math.TimerEMA;

import java.util.Collection;

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
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(ClientProfiler::onCollect);
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

    private static void onCollect(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        timers.add(clientTick);
        timers.add(lastTick);

        right.add(Formatting.LIGHT_PURPLE + String.format("Client TPS:%7.3fms", tps));
    }
}
