package org.orecruncher.dsurround.lib.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DayCycleTests {

    @Test
    void theNightRunsFromSunsetToSunrise() {
        // Noon is 0 degrees, midnight 180: sunset begins at 79, sunrise at 259
        assertEquals(0F, DayCycle.nightProgress(0F));
        assertEquals(0F, DayCycle.nightProgress(79F));
        assertEquals(0.5F, DayCycle.nightProgress(169F), 1e-5F);
        assertEquals(1F, DayCycle.nightProgress(259F));
        assertEquals(1F, DayCycle.nightProgress(300F));
    }

    @Test
    void itOnlyGoesForward() {
        float last = 0F;
        for (float angle = 0F; angle < 360F; angle += 0.5F) {
            float progress = DayCycle.nightProgress(angle);
            assertTrue(progress >= last);
            last = progress;
        }
    }
}
