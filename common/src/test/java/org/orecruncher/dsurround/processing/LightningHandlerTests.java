package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.lightning.Strike;
import org.orecruncher.dsurround.effects.lightning.Strikes;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

public class LightningHandlerTests {

    private static Strike ground(float distance, int boltId) {
        return Strikes.ground(0D, 200D, 0D, 0F, -130F, 0F, 0.5F, distance, true, 0D, boltId, Randomizer.create(1L));
    }

    @Test
    void closeStrikesInTheSkyFlashIt() {
        assertTrue(LightningHandler.flashesSky(ground(100F, -1)));
        assertTrue(LightningHandler.flashesSky(Strikes.spider(0D, 180D, 0D, 100F, -5F, 0F, 0.5F, 100F, true, 0D, Randomizer.create(2L))));
    }

    @Test
    void farStrikesFlashesInTheCloudAndTheGamesBoltsDont() {
        assertFalse(LightningHandler.flashesSky(ground(Strikes.FLASH_RANGE + 1F, -1)));
        assertFalse(LightningHandler.flashesSky(Strikes.sheet(0D, 180D, 0D, 0.5F, 80F, true, 0D, Randomizer.create(3L))));
        // The game flashes the sky for its own
        assertFalse(LightningHandler.flashesSky(ground(20F, 12)));
    }
}
