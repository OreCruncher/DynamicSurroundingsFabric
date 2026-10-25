package org.orecruncher.dsurround.effects.clouds;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the light inside the clouds: what is kept when more is offered than the shader takes.
 */
public class CloudLightsTests {

    @Test
    void keepsWhatFits() {
        var lights = new CloudLights();
        lights.add(1F, 2F, 3F, 40F, 0.5F);
        assertEquals(1, lights.count());
        assertEquals(1F, lights.x(0));
        assertEquals(2F, lights.y(0));
        assertEquals(3F, lights.z(0));
        assertEquals(40F, lights.reach(0));
        assertEquals(0.5F, lights.power(0));
        assertEquals(0F, lights.power(1), "unused");
    }

    @Test
    void tooFaintOrWithNoReachIsLeftOut() {
        var lights = new CloudLights();
        lights.add(0F, 0F, 0F, 40F, CloudLights.MIN_POWER / 2F);
        lights.add(0F, 0F, 0F, 0F, 1F);
        assertEquals(0, lights.count());
    }

    @Test
    void whenFullTheStrongestAreKept() {
        var lights = new CloudLights();
        // Powers 0.1 to 1.2, offered weakest last and strongest in the middle
        float[] offered = {0.5F, 0.3F, 1.2F, 0.9F, 0.1F, 0.7F, 1.1F, 0.2F, 0.8F, 1.0F, 0.4F, 0.6F};
        for (float power : offered)
            lights.add(power, 0F, 0F, 30F, power);
        assertEquals(CloudLights.MAX, lights.count());
        var kept = new HashSet<Float>();
        for (int i = 0; i < lights.count(); i++) {
            kept.add(lights.power(i));
            // Each light kept with its own position
            assertEquals(lights.power(i), lights.x(i));
        }
        for (float power : new float[]{0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F, 1.1F, 1.2F})
            assertTrue(kept.contains(power), "kept " + power);
    }

    @Test
    void clearingEmptiesIt() {
        var lights = new CloudLights();
        lights.add(0F, 0F, 0F, 30F, 1F);
        lights.clear();
        assertEquals(0, lights.count());
        assertEquals(0F, lights.power(0));
    }
}
