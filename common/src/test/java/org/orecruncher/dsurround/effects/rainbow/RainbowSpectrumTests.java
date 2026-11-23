// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rainbow's colors: worked out from how light goes through raindrops, they should come out where real
 * rainbows are, in the order real ones have them, and the shaders should agree with them on what they share.
 */
public class RainbowSpectrumTests {

    // The renderer's shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final RainbowSpectrum SPECTRUM = RainbowSpectrum.create();
    private static final int RED = 0;
    private static final int BLUE = 2;

    // The angle, in degrees, at which a channel of a row is brightest between two angles
    private static float peak(float[] row, int channel, float from, float to) {
        int best = RainbowSpectrum.indexOf(from);
        for (int i = RainbowSpectrum.indexOf(from); i <= RainbowSpectrum.indexOf(to); i++)
            if (row[i * 3 + channel] > row[best * 3 + channel])
                best = i;
        return RainbowSpectrum.degreesOf(best);
    }

    private static float brightness(float[] row, float degrees) {
        int i = RainbowSpectrum.indexOf(degrees);
        return 0.2126F * row[i * 3] + 0.7152F * row[i * 3 + 1] + 0.0722F * row[i * 3 + 2];
    }

    @Test
    void waterBendsVioletMoreThanRed() {
        assertEquals(1.343, RainbowSpectrum.refractiveIndex(400), 0.002);
        assertEquals(1.331, RainbowSpectrum.refractiveIndex(700), 0.002);
    }

    @Test
    void thePrimaryBowIsAt42DegreesRedOutside() {
        var primary = SPECTRUM.primary();
        float red = peak(primary, RED, 30F, 48F);
        float blue = peak(primary, BLUE, 30F, 48F);
        assertEquals(42F, red, 1F, "red");
        assertEquals(40.8F, blue, 1.2F, "blue");
        assertTrue(red > blue, "red is outside blue: " + red + " " + blue);
    }

    @Test
    void theSecondaryBowIsAt51DegreesRedInside() {
        var secondary = SPECTRUM.secondary();
        float red = peak(secondary, RED, 46F, 58F);
        float blue = peak(secondary, BLUE, 46F, 58F);
        assertEquals(50.5F, red, 1.2F, "red");
        assertEquals(53F, blue, 1.5F, "blue");
        assertTrue(red < blue, "red is inside blue: " + red + " " + blue);
    }

    @Test
    void theSecondaryIsFainter() {
        float primary = brightness(SPECTRUM.primary(), peak(SPECTRUM.primary(), 1, 30F, 48F));
        float secondary = brightness(SPECTRUM.secondary(), peak(SPECTRUM.secondary(), 1, 46F, 58F));
        assertTrue(secondary < primary * 0.7F, "secondary " + secondary + " against " + primary);
        // About 15% at its peak with the bands softened by 0.3 degrees, the primary's caustic being the sharper
        assertTrue(secondary > primary * 0.1F, "secondary " + secondary + " against " + primary);
    }

    @Test
    void theSkyIsBrighterInsideThePrimaryThanInAlexandersBand() {
        var primary = SPECTRUM.primary();
        var secondary = SPECTRUM.secondary();
        float inside = brightness(primary, 36F) + brightness(secondary, 36F);
        float band = brightness(primary, 46.5F) + brightness(secondary, 46.5F);
        assertTrue(inside > 0.05F, "inside " + inside);
        assertTrue(band < inside * 0.1F, "band " + band + " inside " + inside);
    }

    @Test
    void thePrimaryIsScaledToOne() {
        float max = 0F;
        for (var value : SPECTRUM.primary()) {
            assertTrue(value >= 0F, "no negative light");
            max = Math.max(max, value);
        }
        assertEquals(1F, max, 1.0E-5F);
        for (var value : SPECTRUM.secondary())
            assertTrue(value >= 0F && value <= 1F, "secondary within range: " + value);
    }

    @Test
    void noLightBeyondTheShadersOutermost() throws IOException {
        // The shader draws nothing further than OUTERMOST from the point opposite the sun: the light there is nearly gone
        var source = ShaderSources.read("core/rainbow.fsh");
        var match = Pattern.compile("OUTERMOST = ([0-9.]+);").matcher(source);
        assertTrue(match.find());
        float outermost = (float) Math.toDegrees(Math.acos(Float.parseFloat(match.group(1))));
        float peak = brightness(SPECTRUM.secondary(), peak(SPECTRUM.secondary(), 1, 46F, 58F));
        float beyond = brightness(SPECTRUM.primary(), outermost) + brightness(SPECTRUM.secondary(), outermost);
        assertTrue(beyond < peak * 0.4F, "at " + outermost + ": " + beyond + " against the secondary's " + peak);
    }

    @Test
    void theShadersRowsSpanTheSameAngles() throws IOException {
        var source = ShaderSources.read("include/rainbow.glsl");
        var match = Pattern.compile("#define MAX_DEGREES ([0-9.]+)").matcher(source);
        assertTrue(match.find());
        assertEquals(RainbowSpectrum.MAX_DEGREES, Float.parseFloat(match.group(1)));
    }

    @Test
    void channelsAreStoredAsSquareRoots() {
        assertEquals(0, RainbowRenderer.channel(0F));
        assertEquals(255, RainbowRenderer.channel(1F));
        assertEquals(128, RainbowRenderer.channel(0.25F), 1);
        assertEquals(0, RainbowRenderer.channel(-1F));
    }
}
