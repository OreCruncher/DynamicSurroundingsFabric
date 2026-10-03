package org.orecruncher.dsurround.gui.overlay.plugins;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

@Cacheable
public final class ClientProfilerPlugin implements IDiagnosticPlugin {

    private final TimerEMA clientTick = new TimerEMA("Client Tick");
    private final TimerEMA lastTick = new TimerEMA("Last Tick");
    private long lastTickMark = -1;
    private long timeMark = 0;
    private float tps = 0;

    public ClientProfilerPlugin() {
        ICollectDiagnostics.EVENT.register(this::onCollect, HandlerPriority.VERY_HIGH);
        IClientTickStart.EVENT.register(this::tickStart, HandlerPriority.VERY_HIGH);
        IClientTickEnd.EVENT.register(this::tickEnd, HandlerPriority.VERY_LOW);
    }

    private void tickStart(Minecraft client) {
        this.timeMark = System.nanoTime();
        if (this.lastTickMark != -1) {
            this.lastTick.update(this.timeMark - this.lastTickMark);
            this.tps = Mth.clamp((float) (50F / this.lastTick.getMSecs() * 20F), 0F, 20F);
        }
        this.lastTickMark = this.timeMark;
    }

    private void tickEnd(Minecraft client) {
        final long delta = System.nanoTime() - this.timeMark;
        this.clientTick.update(delta);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        var tpsTimer = new ITimer() {
            @Override
            public double getMSecs() {
                return tps;
            }

            @Override
            public String toString() {
                // Ticks per second, not a time
                return String.format("Client TPS:%7.3f", this.getMSecs());
            }
        };

        event.add(tpsTimer);
        event.add(clientTick);
        event.add(lastTick);
    }
}
