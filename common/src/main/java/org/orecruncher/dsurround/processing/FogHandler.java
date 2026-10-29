package org.orecruncher.dsurround.processing;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ARGB;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogData lastData;
    // The sky's color while it is tinted; the sky is drawn on the render thread only
    private final float[] skyColor = new float[3];
    // The sunrise or sunset glow's color while it is tinted
    private final float[] sunsetColor = new float[4];

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

    /**
     * How far the sky's fog reaches this frame, in blocks, with the mod's own thickening
     */
    /**
     * How far the view reaches in the fog, in blocks, as of the last frame: the weather's fog or the render distance's,
     * whichever is nearer
     */
    public float viewReach() {
        return Math.min(this.lastData.environmentalEnd, this.lastData.renderDistanceEnd);
    }

    public float skyFogEnd() {
        return this.lastData.skyEnd;
    }

    /**
     * Turns the fog's color toward the dust's in a dust storm. Called from the game's fog color set up.
     *
     * @param rgb the fog's color, red, green and blue from 0 to 1; changed in place
     */
    public void tintFogColor(float[] rgb, float partialTick) {
        this.fogCalculator.tintFogColor(rgb, partialTick);
    }

    /**
     * The sky's color, 0xAARRGGBB, turned toward the dust's in a dust storm as the fog's is. Called as the sky is
     * set up for drawing.
     */
    public int tintSkyColor(int color, float partialTick) {
        var rgb = this.skyColor;
        rgb[0] = ARGB.redFloat(color);
        rgb[1] = ARGB.greenFloat(color);
        rgb[2] = ARGB.blueFloat(color);
        this.fogCalculator.tintFogColor(rgb, partialTick);
        return ARGB.colorFromFloat(ARGB.alphaFloat(color), rgb[0], rgb[1], rgb[2]);
    }

    /**
     * The glow of sunrise and sunset, 0xAARRGGBB, turned toward the dust's color in a dust storm and dimmed. Called as
     * the sky is set up for drawing.
     */
    public int tintSunsetColor(int color, float partialTick) {
        var rgba = this.sunsetColor;
        rgba[0] = ARGB.redFloat(color);
        rgba[1] = ARGB.greenFloat(color);
        rgba[2] = ARGB.blueFloat(color);
        rgba[3] = ARGB.alphaFloat(color);
        this.fogCalculator.tintSunsetColor(rgba, partialTick);
        return ARGB.colorFromFloat(rgba[3], rgba[0], rgba[1], rgba[2]);
    }

    /**
     * The glow of sunrise and sunset, turned toward the dust's color in a dust storm and dimmed. Called as the sky is
     * drawn. The game's own array is shared, so a copy is tinted.
     *
     * @param color the glow's color, red, green, blue and alpha, or null when there is none
     */
    public float[] tintSunsetColor(float[] color, float partialTick) {
        if (color == null)
            return null;
        var rgba = this.sunsetColor;
        System.arraycopy(color, 0, rgba, 0, 4);
        this.fogCalculator.tintSunsetColor(rgba, partialTick);
        return rgba;
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
