package org.orecruncher.dsurround.processing;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.systems.RandomBlockEffectSystem;
import org.orecruncher.dsurround.effects.systems.SteamEffectSystem;
import org.orecruncher.dsurround.effects.systems.WaterfallEffectSystem;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.scanner.SystemsScanner;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Collection;

public class AreaBlockEffects extends AbstractClientHandler {

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    protected ScanContext locus;
    protected SystemsScanner effectSystems;
    protected int blockUpdateCount;
    protected int maxUpdateCount;
    private boolean isConnected = false;

    public AreaBlockEffects(IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, Configuration config, IModLog logger) {
        super("Area Block Effects", config, logger);

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
        IBlockUpdates.EVENT.register(this::blockUpdates);
        IChunkLoad.EVENT.register(this::chunkLoaded);

        // Whenever things reload need to rescan the area. Runs after the libraries, which reload at HIGH and
        // VERY_HIGH, so the rescan sees the new data.
        IReloadEvent.EVENT.register(this::clear, HandlerPriority.LOW);
    }

    @Override
    public void process(final Player player) {
        this.blockUpdateCount = 0;

        // Possible that a client connected to a server, but is being transferred (BungeeCord)
        if (!this.isConnected || !GameUtils.isInGame())
            return;

        if (this.effectSystems != null)
            this.effectSystems.tick();
    }

    @Override
    public void onConnect() {
        this.locus = new ScanContext(
                () -> GameUtils.getWorld().orElseThrow(),
                () -> GameUtils.getPlayer().orElseThrow().blockPosition(),
                this.logger
        );

        this.effectSystems = new SystemsScanner(this.config, this.locus);
        this.effectSystems.addEffectSystem(new SteamEffectSystem(this.logger, this.config));
        this.effectSystems.addEffectSystem(new WaterfallEffectSystem(this.logger, this.config, this.audioPlayer));
        this.effectSystems.addEffectSystem(new RandomBlockEffectSystem(this.logger, this.config, this.blockLibrary, this.audioPlayer, RandomBlockEffectSystem.NEAR_RANGE));
        this.effectSystems.addEffectSystem(new RandomBlockEffectSystem(this.logger, this.config, this.blockLibrary, this.audioPlayer, RandomBlockEffectSystem.FAR_RANGE));

        this.isConnected = true;
    }

    @Override
    public void onDisconnect() {
        this.isConnected = false;
        this.locus = null;
        this.effectSystems = null;
    }

    private void clear(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        // Possible that a client connected to a server, but is being transferred (BungeeCord)
        if (this.effectSystems != null && GameUtils.isInGame())
            this.effectSystems.resetFullScan();
    }

    private void blockUpdates(Collection<BlockPos> blockPositions) {
        // Need to pump the updates through to the effect system. The cuboid scanner
        // will handle the details for filtering and applying updates via blockScan().
        var count = blockPositions.size();
        this.maxUpdateCount = Math.max(count, this.maxUpdateCount);
        this.blockUpdateCount = count;
        // Possible that a client connected to a server, but is being transferred (BungeeCord)
        if (this.effectSystems != null && GameUtils.isInGame())
            this.effectSystems.onBlockUpdates(blockPositions);
    }

    private void chunkLoaded(ClientLevel level, ChunkPos chunkPos) {
        // Chunks keep arriving after the initial scan (joining, respawning, long scan ranges). The scanner queues
        // the newly loaded part of its volume so those blocks aren't missed.
        if (this.effectSystems != null && GameUtils.isInGame())
            this.effectSystems.onChunkLoaded(level, chunkPos);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.Systems);
        panelText.add(Component.literal("Block Updates: %d (max: %d)".formatted(this.blockUpdateCount, this.maxUpdateCount)));
        if (this.effectSystems != null)
            this.effectSystems.gatherDiagnostics(panelText);
    }
}
