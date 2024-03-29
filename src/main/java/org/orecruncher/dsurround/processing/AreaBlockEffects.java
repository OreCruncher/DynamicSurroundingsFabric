package org.orecruncher.dsurround.processing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.effects.systems.RandomBlockEffectSystem;
import org.orecruncher.dsurround.effects.systems.SteamEffectSystem;
import org.orecruncher.dsurround.effects.systems.WaterfallEffectSystem;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
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
    private boolean isConnected = false;

    public AreaBlockEffects(IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, Configuration config, IModLog logger) {
        super("Area Block Effects", config, logger);

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
        ClientEventHooks.BLOCK_UPDATE.register(this::blockUpdates);

        // Whenever things reload need to rescan the area
        AssetLibraryEvent.RELOAD.register(this::clear);
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
        this.effectSystems.addEffectSystem(new WaterfallEffectSystem(this.logger, this.config));
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
        this.blockUpdateCount = blockPositions.size();
        // Possible that a client connected to a server, but is being transferred (BungeeCord)
        if (this.effectSystems != null && GameUtils.isInGame())
            this.effectSystems.onBlockUpdates(blockPositions);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.Systems);
        panelText.add(Component.literal("Block Updates: %d".formatted(this.blockUpdateCount)));
        if (this.effectSystems != null)
            this.effectSystems.gatherDiagnostics(panelText);
    }
}
