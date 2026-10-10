package org.orecruncher.dsurround.processing;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogData lastData;

    public FogHandler(HolisticFogRangeCalculator fogCalculator, Configuration config, IModLog logger) {
        super("Fog Handler", config, logger);

        this.fogCalculator = fogCalculator;
        this.lastData = new FogData();
        this.lastData.environmentalStart = this.lastData.environmentalEnd = 192F;

        IFogRender.EVENT.register(this::renderFog);
    }

    @Override
    public void process(final Player player) {
        if (this.fogCalculator.enabled())
            this.fogCalculator.tick();
    }

    @Override
    public void onDisconnect() {
        this.fogCalculator.disconnect();
    }

    private void renderFog(FogData data, float renderDistance, float partialTick) {
        if (this.fogCalculator.enabled()) {
            this.lastData = this.fogCalculator.render(data, renderDistance, partialTick);
            FogCompat.applyRange(data, this.lastData);
        } else {
            // Preserve for diagnostic trace even though action was not taken
            this.lastData = data;
        }
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Fog: %f/%f".formatted(this.lastData.environmentalStart, this.lastData.environmentalEnd);
        var disabledText = this.fogCalculator.getDisabledText();
        if (disabledText.isPresent())
            text += disabledText.get();
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
