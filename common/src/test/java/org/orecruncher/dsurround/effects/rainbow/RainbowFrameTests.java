// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rainbow's frame values: the point opposite the sun is where the game's sun isn't, and the values sit
 * where the shader's block has them.
 */
public class RainbowFrameTests {

    private static final float EPSILON = 1.0E-5F;

    @Test
    void oppositeTheSunAtSunriseIsWest() {
        // The sun rises in the east (+x), at a celestial angle of 270
        var antisolar = RainbowFrame.antisolar(270F);
        assertEquals(-1F, antisolar.x, EPSILON);
        assertEquals(0F, antisolar.y, EPSILON);
    }

    @Test
    void oppositeTheSunInTheAfternoonIsEastAndBelowTheHorizon() {
        // The sun 20 degrees up in the west: the point opposite it 20 degrees down in the east
        var antisolar = RainbowFrame.antisolar(70F);
        assertTrue(antisolar.x > 0F);
        assertEquals(-20F, (float) Math.toDegrees(Math.asin(antisolar.y)), 1.0E-3F);
        assertEquals(1F, antisolar.length(), EPSILON);
    }

    @Test
    void theTopOfTheBowIsSquareToTheCenterAndUp() {
        var antisolar = RainbowFrame.antisolar(70F);
        var across = RainbowFrame.across(antisolar);
        assertEquals(0F, across.dot(antisolar), EPSILON);
        assertEquals(1F, across.length(), EPSILON);
        assertTrue(across.y > 0F);
        // The top of the primary bow: 42 degrees from the center toward it, 22 degrees up
        var top = new Vector3f(antisolar).mul((float) Math.cos(Math.toRadians(42)))
                .add(new Vector3f(across).mul((float) Math.sin(Math.toRadians(42))));
        assertEquals(22F, (float) Math.toDegrees(Math.asin(top.y)), 1.0E-2F);
    }

    @Test
    void theTopIsStillSquareWithTheSunOverhead() {
        var across = RainbowFrame.across(RainbowFrame.antisolar(0F));
        assertEquals(1F, across.length(), EPSILON);
    }

    @Test
    void sunlightReddensNearTheHorizon() {
        var high = RainbowFrame.sunlight(30F);
        var low = RainbowFrame.sunlight(1F);
        assertEquals(1F, high.y, EPSILON);
        assertEquals(1F, high.z, EPSILON);
        assertTrue(low.z < low.y && low.y < low.x);
    }

    @Test
    void valuesSitWhereTheBlockHasThem() {
        var frame = RainbowFrame.of(70F, 1.5F, 0.25F, new Matrix4f().perspective(1.2F, 1.7F, 0.05F, 1000F), new Matrix4f(), 12F, 7F, true);
        var v = frame.vec4s();
        assertEquals(RainbowFrame.VECTORS * 4, v.length);
        var antisolar = RainbowFrame.antisolar(70F);
        assertEquals(antisolar.x, v[RainbowFrame.ANTISOLAR], EPSILON);
        assertEquals(1F, v[RainbowFrame.STRENGTH], "strength clamped");
        assertEquals(RainbowFrame.across(antisolar).y, v[RainbowFrame.ACROSS + 1], EPSILON);
        assertEquals(0.25F, v[RainbowFrame.SECONDARY]);
        assertEquals(7F, v[12]);
        assertEquals(1F, v[RainbowFrame.BLOCKY]);
        var realistic = RainbowFrame.of(70F, 1F, 0F, new Matrix4f(), new Matrix4f(), 0F, 0F, false);
        assertEquals(0F, realistic.vec4s()[RainbowFrame.BLOCKY]);
    }
}
