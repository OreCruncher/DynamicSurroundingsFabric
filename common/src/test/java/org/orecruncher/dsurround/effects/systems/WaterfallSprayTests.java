package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.ParticleStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WaterfallSprayTests {

    @Test
    void theSameMistWhateverTheStrength() {
        assertEquals(WaterfallSpray.MIST_PUFFS, WaterfallSpray.mistCount(ParticleStatus.ALL, false));
    }

    @Test
    void halfAsMuchMistWithFewerParticlesOrFarAway() {
        assertEquals(WaterfallSpray.MIST_PUFFS / 2, WaterfallSpray.mistCount(ParticleStatus.DECREASED, false));
        assertEquals(WaterfallSpray.MIST_PUFFS / 2, WaterfallSpray.mistCount(ParticleStatus.ALL, true));
    }

    @Test
    void moreFoamForABiggerWaterfall() {
        assertEquals(2, WaterfallSpray.foamCount(0, ParticleStatus.ALL, false));
        assertEquals(3, WaterfallSpray.foamCount(3, ParticleStatus.ALL, false));
        assertEquals(5, WaterfallSpray.foamCount(10, ParticleStatus.ALL, false));
        for (int strength = 0; strength < 10; strength++)
            assertTrue(WaterfallSpray.foamCount(strength + 1, ParticleStatus.ALL, false) >= WaterfallSpray.foamCount(strength, ParticleStatus.ALL, false));
    }

    @Test
    void reducedFoamIsHalvedButNeverNone() {
        assertEquals(1, WaterfallSpray.foamCount(0, ParticleStatus.DECREASED, false));
        assertEquals(1, WaterfallSpray.foamCount(0, ParticleStatus.ALL, true));
        assertEquals(2, WaterfallSpray.foamCount(10, ParticleStatus.DECREASED, true));
    }

    @Test
    void foamIsThrownFasterByABiggerWaterfall() {
        assertEquals(0.06, WaterfallSpray.foamMinSpeed(0), 1e-9);
        assertEquals(0.16, WaterfallSpray.foamMinSpeed(10), 1e-9);
    }
}
