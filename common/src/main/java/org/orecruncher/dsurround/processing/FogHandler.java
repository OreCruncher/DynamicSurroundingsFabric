package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogRenderer.FogData lastData;

    public FogHandler(Configuration config, IModLog logger) {
        super("Fog Handler", config, logger);

        this.fogCalculator = new HolisticFogRangeCalculator(logger, config.fogOptions);
        this.lastData = new FogRenderer.FogData(FogRenderer.FogMode.FOG_TERRAIN);
        this.lastData.start = this.lastData.end = 192F;

        ClientEventHooks.FOG_RENDER_EVENT.register(this::renderFog);
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
        if (this.fogCalculator.enabled()) {
            this.lastData = this.fogCalculator.render(data, renderDistance, partialTick);
            RenderSystem.setShaderFogStart(this.lastData.start);
            RenderSystem.setShaderFogEnd(this.lastData.end);
            RenderSystem.setShaderFogShape(this.lastData.shape);
        }
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        if (this.fogCalculator.enabled())
            event.add(CollectDiagnosticsEvent.Section.Systems, "Fog: %f/%f, %s, %s".formatted(this.lastData.start, this.lastData.end, this.lastData.shape, this.lastData.mode));
        else
            event.add(CollectDiagnosticsEvent.Section.Systems, "Fog: DISABLED");
    }
}
