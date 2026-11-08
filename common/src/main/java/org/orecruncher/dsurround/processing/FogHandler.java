// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.lib.compat.FogCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.fog.ExtendedViewFog;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;

public class FogHandler extends AbstractClientHandler {

    private final HolisticFogRangeCalculator fogCalculator;
    private FogData lastData;
    // The sky's color while it is tinted; the sky is drawn on the render thread only
    private final float[] skyColor = new float[3];
    // The sunrise or sunset glow's color while it is tinted
    private final float[] sunsetColor = new float[4];
    // Whether another mod had the view reaching past the render distance when the last frame's fog was finished
    // (a distant terrain mod): our fog is then held back and added after it (see ExtendedViewFog)
    private boolean viewExtended;
    // This frame's fog as the game gave it to us, and what we made of it, while held back; null otherwise
    private FogData heldGiven;
    private FogData heldOurs;

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
        this.viewExtended = false;
        this.heldGiven = this.heldOurs = null;
    }

    private void renderFog(FogData data, float renderDistance, float partialTick) {
        this.heldGiven = this.heldOurs = null;
        if (this.fogCalculator.enabled() && this.viewExtended) {
            // Worked out on a copy, the game's left as it is for the other mod to stretch
            this.heldGiven = FogCompat.copy(data);
            this.heldOurs = this.fogCalculator.render(FogCompat.copy(data), renderDistance, partialTick);
            this.lastData = this.heldOurs;
        } else if (this.fogCalculator.enabled()) {
            this.lastData = this.fogCalculator.render(data, renderDistance, partialTick);
            FogCompat.applyRange(data, this.lastData);
        } else {
            // Preserve for diagnostic trace even though action was not taken
            this.lastData = data;
        }
    }

    /**
     * Called once the game and every other mod have set the frame's fog: adds our fog if it was held back, and notes
     * whether the view reaches past the render distance, for the next frame. Render thread.
     *
     * @param data           the frame's finished fog; changed in place
     * @param renderDistance the render distance in blocks
     */
    public void finishFog(FogData data, float renderDistance) {
        this.viewExtended = ExtendedViewFog.isExtended(data, renderDistance);
        var given = this.heldGiven;
        var ours = this.heldOurs;
        this.heldGiven = this.heldOurs = null;
        if (given == null || ours == null)
            return;
        ExtendedViewFog.apply(data, given, ours, renderDistance);
        this.lastData = FogCompat.copy(data);
    }

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
    public Vector3fc tintSkyColor(Vector3fc color, float partialTick) {
        var rgb = this.skyColor;
        rgb[0] = color.x();
        rgb[1] = color.y();
        rgb[2] = color.z();
        this.fogCalculator.tintFogColor(rgb, partialTick);
        return new Vector3f(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * The glow of sunrise and sunset, 0xAARRGGBB, turned toward the dust's color in a dust storm and dimmed. Called as
     * the sky is set up for drawing.
     */
    public Vector4fc tintSunsetColor(Vector4fc color, float partialTick) {
        var rgba = this.sunsetColor;
        rgba[0] = color.x();
        rgba[1] = color.y();
        rgba[2] = color.z();
        rgba[3] = color.w();
        this.fogCalculator.tintSunsetColor(rgba, partialTick);
        return new Vector4f(rgba[0], rgba[1], rgba[2], rgba[3]);
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
        if (this.viewExtended)
            text += " (view past render distance)";
        var disabledText = this.fogCalculator.getDisabledText();
        if (disabledText.isPresent())
            text += disabledText.get();
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
