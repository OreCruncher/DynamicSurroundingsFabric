package org.orecruncher.dsurround.processing;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogRenderer.FogData lastData;

    public FogHandler(HolisticFogRangeCalculator fogCalculator, Configuration config, IModLog logger) {
        super("Fog Handler", config, logger);

        this.fogCalculator = fogCalculator;
        this.lastData = new FogRenderer.FogData(FogRenderer.FogMode.FOG_TERRAIN);
        this.lastData.start = this.lastData.end = 192F;

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

    private void renderFog(FogRenderer.FogData data, float renderDistance, float partialTick) {
        var result = data;
        if (this.fogCalculator.enabled()) {
            result = this.fogCalculator.render(data, renderDistance, partialTick);
            FogCompat.applyShaderFog(result);
        }

        // The diagnostic trace shows the terrain fog, even when no action was taken; the sky's is set too
        if (data.mode == FogRenderer.FogMode.FOG_TERRAIN)
            this.lastData = result;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Fog: %f/%f, %s, %s ".formatted(this.lastData.start, this.lastData.end, this.lastData.shape, this.lastData.mode);
        var disabledText = this.fogCalculator.getDisabledText();
        if (disabledText.isPresent())
            text += disabledText.get();
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
