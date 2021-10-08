package org.orecruncher.dsurround.processing;

import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.lib.scanner.ClientPlayerContext;
import org.orecruncher.dsurround.processing.scanner.RandomBlockEffectScanner;

public class AreaBlockEffects extends ClientHandler {

    protected ClientPlayerContext locus;
    protected RandomBlockEffectScanner nearEffects;
    protected RandomBlockEffectScanner farEffects;

    AreaBlockEffects() {
        super("Area Block Effects");
    }

    @Override
    public void process(final PlayerEntity player) {
        this.nearEffects.tick();
        this.farEffects.tick();
    }

    @Override
    public void onConnect() {
        this.locus = new ClientPlayerContext();
        this.nearEffects = new RandomBlockEffectScanner(this.locus, RandomBlockEffectScanner.NEAR_RANGE);
        this.farEffects = new RandomBlockEffectScanner(this.locus, RandomBlockEffectScanner.FAR_RANGE);
    }

    @Override
    public void onDisconnect() {
        this.locus = null;
        this.nearEffects = null;
        this.farEffects = null;
    }
}
