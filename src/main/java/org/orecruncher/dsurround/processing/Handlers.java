package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.lib.threading.IClientTasking;
import org.orecruncher.dsurround.lib.world.WorldUtils;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

@Environment(EnvType.CLIENT)
public class Handlers {

    private final Configuration config;
    private final IModLog logger;
    private final IClientTasking tasking;
    private final ITickCount tickCount;
    private final ISoundLibrary soundLibrary;
    private final IAudioPlayer audioPlayer;
    private final ObjectArray<ClientHandler> effectHandlers = new ObjectArray<>();
    private final LoggingTimerEMA handlerTimer = new LoggingTimerEMA("Handlers");
    private boolean isConnected = false;
    private boolean startupSoundPlayed = false;

    public Handlers(Configuration config, IModLog logger, IClientTasking tasking, ITickCount tickCount, ISoundLibrary soundLibrary, IAudioPlayer audioPlayer) {
        this.config = config;
        this.logger = logger;
        this.tasking = tasking;
        this.tickCount = tickCount;
        this.soundLibrary = soundLibrary;
        this.audioPlayer = audioPlayer;
        init();
    }

    protected static PlayerEntity getPlayer() {
        return GameUtils.getPlayer();
    }

    private void register(final Class<? extends ClientHandler> clazz) {
        var handler = ContainerManager.resolve(clazz);
        this.effectHandlers.add(handler);
        this.logger.debug("Registered handler [%s]", handler.getHandlerName());
    }

    private void init() {
        // If the user disabled the startup sound just flag it as having
        // been performed.
        this.startupSoundPlayed = !config.otherOptions.playRandomSoundOnStartup;

        register(Scanners.class);           // Must be first
        register(PlayerHandler.class);
        register(EntityEffectHandler.class);
        register(BiomeSoundHandler.class);
        register(AreaBlockEffects.class);

        ClientState.TICK_END.register(this::tick);
        ClientState.ON_CONNECT.register(this::onConnect);
        ClientState.ON_DISCONNECT.register(this::onDisconnect);

        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::gatherDiagnostics, HandlerPriority.HIGH);
    }

    private void onConnect(MinecraftClient client) {
        try {
            this.tasking.execute(() -> {
                this.logger.info("Client connecting...");
                if (this.isConnected) {
                    this.logger.warn("Attempt to initialize EffectManager when it is already initialized");
                    onDisconnect(client);
                }
                for (final ClientHandler h : this.effectHandlers)
                    h.connect0();
                this.isConnected = true;
            });
        } catch (Exception ex) {
            this.logger.error(ex, "Unable to perform client connect");
        }
    }

    private void onDisconnect(MinecraftClient client) {
        try {
            this.tasking.execute(() -> {
                this.logger.info("Client disconnecting...");
                this.isConnected = false;
                for (final ClientHandler h : this.effectHandlers)
                    h.disconnect0();
            });
        } catch (Exception ex) {
            this.logger.error(ex, "Unable to perform client disconnect");
        }
    }

    protected boolean doTick() {
        return GameUtils.isInGame()
                && !GameUtils.isPaused()
                && !(GameUtils.getCurrentScreen() instanceof IndividualSoundControlScreen)
                && isPlayerChunkLoaded();
    }

    protected boolean isPlayerChunkLoaded() {
        var player = GameUtils.getPlayer();
        var pos = player.getBlockPos();
        return WorldUtils.isChunkLoaded(player.getEntityWorld(), pos);
    }

    public void tick(MinecraftClient client) {
        if (!this.startupSoundPlayed)
            handleStartupSound();

        if (!doTick())
            return;

        this.handlerTimer.begin();
        final long tick = this.tickCount.getTickCount();

        for (final ClientHandler handler : this.effectHandlers) {
            final long mark = System.nanoTime();
            if (handler.doTick(tick))
                handler.process(getPlayer());
            handler.updateTimer(System.nanoTime() - mark);
        }
        this.handlerTimer.end();
    }

    private void handleStartupSound() {
        var client = GameUtils.getMC();
        if (client.getOverlay() != null)
            return;

        this.startupSoundPlayed = true;

        this.soundLibrary
            .getRandomStartupSound()
            .ifPresent(id -> {
                var sound = SoundFactoryBuilder
                        .create(id)
                        .build()
                        .createAsAdditional();
                this.audioPlayer.play(sound);
            });
    }

    public void gatherDiagnostics(ClientEventHooks.CollectDiagnosticsEvent event) {
        event.timers.add(this.handlerTimer);

        this.effectHandlers.forEach(h -> {
            h.gatherDiagnostics(event.left, event.right, event.timers);
            event.timers.add(h.getTimer());
        });
    }

    public static void registerHandlers() {
        // Register so that Scanners can be instantiated
        ContainerManager.getDefaultContainer()
            .registerSingleton(CeilingScanner.class)
            .registerSingleton(VillageScanner.class)
            .registerSingleton(BiomeScanner.class)
            .registerSingleton(Scanners.class)
            .registerSingleton(PlayerHandler.class)
            .registerSingleton(EntityEffectHandler.class)
            .registerSingleton(BiomeSoundHandler.class)
            .registerSingleton(AreaBlockEffects.class)
            .registerSingleton(Handlers.class);
    }
}
