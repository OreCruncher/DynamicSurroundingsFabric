package org.orecruncher.dsurround.processing;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.lib.scanner.ClientPlayerContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;
import org.orecruncher.dsurround.processing.scanner.AlwaysOnBlockEffectScanner;
import org.orecruncher.dsurround.processing.scanner.RandomBlockEffectScanner;

import java.util.Collection;

public class AreaBlockEffects extends ClientHandler {

    protected ClientPlayerContext locus;
    protected BlockEffectManager blockEffects;
    protected RandomBlockEffectScanner nearEffects;
    protected RandomBlockEffectScanner farEffects;
    protected AlwaysOnBlockEffectScanner alwaysOn;

    AreaBlockEffects() {
        super("Area Block Effects");

        ClientEventHooks.BLOCK_UPDATE.register(this::blockUpdates);
    }

    @Override
    public void process(final PlayerEntity player) {
        this.blockEffects.tick(player);
        this.nearEffects.tick();
        this.farEffects.tick();
        this.alwaysOn.tick();
    }

    @Override
    public void onConnect() {
        this.locus = new ClientPlayerContext();
        this.blockEffects = new BlockEffectManager();
        this.alwaysOn = new AlwaysOnBlockEffectScanner(this.locus, this.blockEffects, Client.Config.blockEffects.blockEffectRange);
        this.nearEffects = new RandomBlockEffectScanner(this.locus, this.blockEffects, RandomBlockEffectScanner.NEAR_RANGE);
        this.farEffects = new RandomBlockEffectScanner(this.locus, this.blockEffects, RandomBlockEffectScanner.FAR_RANGE);
    }

    @Override
    public void onDisconnect() {
        this.locus = null;
        this.blockEffects = null;
        this.alwaysOn = null;
        this.nearEffects = null;
        this.farEffects = null;
    }

    private void blockUpdates(Collection<BlockPos> blockPos) {
        if (this.alwaysOn != null) {
            blockPos.forEach(this.alwaysOn::onBlockUpdate);
        }
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        left.add(Formatting.LIGHT_PURPLE + String.format("Total Effects: %d", this.blockEffects.count()));
    }
}
