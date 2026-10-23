package org.orecruncher.dsurround.effects.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how much death mist a body gets.
 */
public class DeathPuffsTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aZombieGetsAboutEight() {
        assertEquals(8, DeathPuffs.puffsFor(0.6F, 1.95F));
    }

    @Test
    void biggerBodiesGetMoreWithinLimits() {
        assertEquals(DeathPuffs.MIN_PUFFS, DeathPuffs.puffsFor(0.4F, 0.7F));
        assertTrue(DeathPuffs.puffsFor(1.4F, 0.9F) > DeathPuffs.puffsFor(0.6F, 1.95F));
        assertEquals(DeathPuffs.MAX_PUFFS, DeathPuffs.puffsFor(3.6F, 12F));
    }
}
