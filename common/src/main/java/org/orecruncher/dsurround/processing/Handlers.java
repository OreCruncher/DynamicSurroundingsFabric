package org.orecruncher.dsurround.processing;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IClientConnect;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.processing.accents.FootstepAccents;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

@Cacheable
public class Handlers {

    private final Configuration config;
    private final IModLog logger;
    private final ITickCount tickCount;
    private final ISoundLibrary soundLibrary;
    private final IAudioPlayer audioPlayer;
    private final ObjectArray<AbstractClientHandler> effectHandlers = new ObjectArray<>();
    private final LoggingTimerEMA handlerTimer = new LoggingTimerEMA("Handlers");
    private boolean isConnected = false;
    private boolean startupSoundPlayed = false;

    public Handlers(Configuration config, IModLog logger, ITickCount tickCount, ISoundLibrary soundLibrary, IAudioPlayer audioPlayer) {
        this.config = config;
        this.logger = logger;
        this.tickCount = tickCount;
        this.soundLibrary = soundLibrary;
        this.audioPlayer = audioPlayer;
        init();
    }

    protected static Player getPlayer() {
        return GameUtils.getPlayer().orElseThrow();
    }

    private void register(final Class<? extends AbstractClientHandler> clazz) {
        var handler = ContainerManager.resolve(clazz);
        this.effectHandlers.add(handler);
        this.logger.debug("Registered handler [%s]", handler.getHandlerName());
    }

    private void init() {

        // If the user disabled the startup sound flag it as having
        // been performed.
        this.startupSoundPlayed = !this.config.otherOptions.playRandomSoundOnStartup;

        this.register(Scanners.class);           // Must be first
        this.register(EntityEffectHandler.class);
        this.register(BiomeSoundHandler.class);
        this.register(AreaBlockEffects.class);
        this.register(StepAccentGenerator.class);
        this.register(FogHandler.class);

        IClientTickEnd.EVENT.register(this::tick);
        IClientConnect.EVENT.register(this::onConnect);
        IClientDisconnect.EVENT.register(this::onDisconnect);

        ICollectDiagnostics.EVENT.register(this::gatherDiagnostics, HandlerPriority.HIGH);
    }

    // Connect and disconnect are raised from the client tick, so these run on the client thread
    private void onConnect(Minecraft client) {
        try {
            this.logger.info("Handlers connecting...");
            if (this.isConnected) {
                this.logger.warn("Attempt to connect when already connected; disconnecting first");
                this.onDisconnect(client);
            }
            this.effectHandlers.forEach(AbstractClientHandler::connect0);
            this.isConnected = true;
        } catch (Exception ex) {
            this.logger.error(ex, "Unable to perform client connect");
        }
    }

    private void onDisconnect(Minecraft client) {
        try {
            this.logger.info("Client disconnecting...");
            this.isConnected = false;
            this.effectHandlers.forEach(AbstractClientHandler::disconnect0);
        } catch (Exception ex) {
            this.logger.error(ex, "Unable to perform client disconnect");
        }
    }

    protected boolean doTick() {
        return GameUtils.isInGame()
                && !GameUtils.isPaused()
                && !(GameUtils.getCurrentScreen().map(s -> s instanceof  IndividualSoundControlScreen).orElse(false))
                && isPlayerChunkLoaded();
    }

    protected boolean isPlayerChunkLoaded() {
        var player = GameUtils.getPlayer().orElseThrow();
        var pos = player.blockPosition();
        return LevelCompat.isChunkLoaded(player.level(), pos);
    }

    public void tick(Minecraft client) {
        if (!this.startupSoundPlayed)
            handleStartupSound();

        if (!doTick())
            return;

        this.handlerTimer.begin();
        final long tick = this.tickCount.getTickCount();
        final Player player = getPlayer();

        for (final AbstractClientHandler handler : this.effectHandlers) {
            final long mark = System.nanoTime();
            if (handler.doTick(tick))
                handler.process(player);
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

    public void gatherDiagnostics(CollectDiagnosticsEvent event) {
        event.add(this.handlerTimer);

        this.effectHandlers.forEach(h -> {
            h.gatherDiagnostics(event);
            event.add(h.getTimer());
        });
    }

    public static void registerHandlers() {
        // Register so that Scanners can be instantiated
        ContainerManager.getRootContainer()
            .registerSingleton(CeilingScanner.class)
            .registerSingleton(VillageScanner.class)
            .registerSingleton(BiomeScanner.class)
            .registerSingleton(Scanners.class)
            .registerSingleton(EntityEffectHandler.class)
            .registerSingleton(BiomeSoundHandler.class)
            .registerSingleton(AreaBlockEffects.class)
            .registerSingleton(FootstepAccents.class)
            .registerSingleton(StepAccentGenerator.class)
            .registerSingleton(HolisticFogRangeCalculator.class)
            .registerSingleton(FogHandler.class)
            .registerSingleton(Handlers.class);
    }
}
