package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Singleton;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.TimerEMA;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class Handlers {

    private static final IModLog LOGGER = Client.LOGGER.createChild(Handlers.class);
    private static final Singleton<Handlers> INSTANCE = new Singleton<>(Handlers::new);

    private final ObjectArray<ClientHandler> effectHandlers = new ObjectArray<>();
    private boolean isConnected = false;

    public static void initialize() {
        INSTANCE.get();
    }

    private Handlers() {
        init();
    }

    private void register(final ClientHandler handler) {
        this.effectHandlers.add(handler);
        LOGGER.debug("Registered handler [%s]", handler.getClass().getName());
    }

    private void init() {
        // This has to be first!
        register(new BiomeSoundHandler());

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::gatherDiagnostics);
        ClientPlayConnectionEvents.JOIN.register(this::onConnect);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
    }

    private void onConnect(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        if (isConnected) {
            LOGGER.warn("Attempt to initialize EffectManager when it is already initialized");
            onDisconnect(null, null);
        }
        isConnected = true;
        for (final ClientHandler h : this.effectHandlers)
            h.connect0();
    }

    private void onDisconnect(ClientPlayNetworkHandler handler, MinecraftClient client) {
        for (final ClientHandler h : this.effectHandlers)
            h.disconnect0();
        isConnected = false;
    }

    protected static PlayerEntity getPlayer() {
        return GameUtils.getPlayer();
    }

    protected boolean checkReady() {
        return GameUtils.isInGame() && !GameUtils.getMC().isPaused();
    }

    public void onTick(MinecraftClient client) {
        if (!checkReady())
            return;

        final long tick = TickCounter.getTickCount();

        for (final ClientHandler handler : this.effectHandlers) {
            final long mark = System.nanoTime();
            if (handler.doTick(tick))
                handler.process(getPlayer());
            handler.updateTimer(System.nanoTime() - mark);
        }
    }

    public void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        if (Client.Config.logging.enableDebugLogging)
            this.effectHandlers.forEach(h -> {
                h.gatherDiagnostics(left, right, timers);
                timers.add(h.getTimer());
            });
    }
}