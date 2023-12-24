package org.orecruncher.dsurround.gui.hud.plugins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.lib.platform.Services;

public class ClientProfilerPlugin implements IDiagnosticPlugin {

    private final TimerEMA clientTick = new TimerEMA("Client Tick");
    private final TimerEMA lastTick = new TimerEMA("Last Tick");
    private long lastTickMark = -1;
    private long timeMark = 0;
    private float tps = 0;

    public ClientProfilerPlugin() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.VERY_HIGH);

        // Go to Fabric for these as it should encompass other mod impacts
        Services.EVENT_REGISTRATIONS.registerClientTickStart(this::tickStart);
        Services.EVENT_REGISTRATIONS.registerClientTickEnd(this::tickEnd);
    }

    private void tickStart(MinecraftClient client) {
        this.timeMark = System.nanoTime();
        if (this.lastTickMark != -1) {
            this.lastTick.update(this.timeMark - this.lastTickMark);
            this.tps = MathHelper.clamp((float) (50F / this.lastTick.getMSecs() * 20F), 0F, 20F);
        }
        this.lastTickMark = this.timeMark;
    }

    private void tickEnd(MinecraftClient client) {
        final long delta = System.nanoTime() - this.timeMark;
        this.clientTick.update(delta);
    }

    public void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
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
