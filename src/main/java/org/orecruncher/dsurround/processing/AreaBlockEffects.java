package org.orecruncher.dsurround.processing;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;
import org.orecruncher.dsurround.processing.scanner.AlwaysOnBlockEffectScanner;
import org.orecruncher.dsurround.processing.scanner.RandomBlockEffectScanner;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Collection;

public class AreaBlockEffects extends AbstractClientHandler {

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    protected ScanContext locus;
    protected BlockEffectManager blockEffects;
    protected RandomBlockEffectScanner nearEffects;
    protected RandomBlockEffectScanner farEffects;
    protected AlwaysOnBlockEffectScanner alwaysOn;

    private boolean isConnected = false;

    public AreaBlockEffects(IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, Configuration config, IModLog logger) {
        super("Area Block Effects", config, logger);

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
        ClientEventHooks.BLOCK_UPDATE.register(this::blockUpdates);
    }

    @Override
    public void process(final PlayerEntity player) {
        if (!this.isConnected)
            return;

        // TODO: Reports that this reduces some crashes.  Not sure how since the connected
        // would prevent this logic from executing because it sets the isConnected
        // flag.
        if (player == null)
            return;

        if (this.blockEffects != null)
            this.blockEffects.tick(player);
        if (this.nearEffects != null)
            this.nearEffects.tick();
        if (this.farEffects != null)
            this.farEffects.tick();
        if (this.alwaysOn != null)
            this.alwaysOn.tick();
    }

    @Override
    public void onConnect() {
        this.locus = new ScanContext(
                () -> GameUtils.getWorld().orElseThrow(),
                () -> GameUtils.getPlayer().map(Entity::getBlockPos).orElseThrow(),
                this.logger,
                () -> GameUtils.getWorld().map(w -> w.getRegistryKey().getValue()).orElseThrow()
        );
        this.blockEffects = new BlockEffectManager(this.config.blockEffects.blockEffectRange);
        this.alwaysOn = new AlwaysOnBlockEffectScanner(this.locus, this.blockLibrary, this.blockEffects, this.config.blockEffects.blockEffectRange);
        this.nearEffects = new RandomBlockEffectScanner(this.locus, this.blockLibrary, this.audioPlayer, this.blockEffects, RandomBlockEffectScanner.NEAR_RANGE);
        this.farEffects = new RandomBlockEffectScanner(this.locus, this.blockLibrary, this.audioPlayer, this.blockEffects, RandomBlockEffectScanner.FAR_RANGE);

        this.isConnected = true;
    }

    @Override
    public void onDisconnect() {
        this.isConnected = false;

        this.locus = null;
        this.blockEffects = null;
        this.alwaysOn = null;
        this.nearEffects = null;
        this.farEffects = null;
    }

    private void blockUpdates(ClientEventHooks.BlockUpdateEvent event) {
        if (this.alwaysOn != null) {
            event.updates().forEach(this.alwaysOn::onBlockUpdate);
        }
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<ITimer> timers) {
        if (this.blockEffects != null)
            left.add(Formatting.LIGHT_PURPLE + String.format("Total Effects: %d", this.blockEffects.count()));
    }
}
