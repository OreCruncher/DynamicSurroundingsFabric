package org.orecruncher.dsurround.processing;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogRenderer.FogData lastData;
    // The sky's colour while it is tinted; the sky is drawn on the render thread only
    private final float[] skyColor = new float[3];

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

    /**
     * Turns the fog's colour toward the dust's in a dust storm. Called from the game's fog colour set up.
     *
     * @param rgb the fog's colour, red, green and blue from 0 to 1; changed in place
     */
    public void tintFogColor(float[] rgb, float partialTick) {
        this.fogCalculator.tintFogColor(rgb, partialTick);
    }

    /**
     * The sky's colour, turned toward the dust's in a dust storm as the fog's is. Called as the sky is drawn.
     */
    public Vec3 tintSkyColor(Vec3 color, float partialTick) {
        var rgb = this.skyColor;
        rgb[0] = (float) color.x;
        rgb[1] = (float) color.y;
        rgb[2] = (float) color.z;
        this.fogCalculator.tintFogColor(rgb, partialTick);
        if (rgb[0] == (float) color.x && rgb[1] == (float) color.y && rgb[2] == (float) color.z)
            return color;
        return new Vec3(rgb[0], rgb[1], rgb[2]);
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
