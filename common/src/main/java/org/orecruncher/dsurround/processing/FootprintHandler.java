package org.orecruncher.dsurround.processing;

import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.footprints.Footprints;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Ages the footprints left by {@link org.orecruncher.dsurround.effects.footprints.FootprintEffect} each tick, and
 * drops them on leaving a world.
 */
public class FootprintHandler extends AbstractClientHandler {

    private final Footprints footprints;

    public FootprintHandler(Footprints footprints, Configuration config, IModLog logger) {
        super("Footprints", config, logger);
        this.footprints = footprints;
    }

    @Override
    public void process(final Player player) {
        this.footprints.tick(player.level());
    }

    @Override
    public void onDisconnect() {
        this.footprints.clear();
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        event.add(CollectDiagnosticsEvent.Section.Systems, "Footprints: %d".formatted(this.footprints.count()));
    }
}
