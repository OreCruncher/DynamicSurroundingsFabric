// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ActorKind;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where and when world actors may start: the same for every player in a world, from the world's seed, the cell, the
 * slot and the kind alone.
 */
public class ActorSpawnsTests {

    private static final long SEED = 0x1234_5678_9ABCL;

    @Test
    void everyoneGetsTheSamePlan() {
        for (int i = 0; i < 100; i++) {
            var a = ActorSpawns.plan(SEED, i - 50, 7 - i, 1000 + i, ActorKind.DUST_DEVIL);
            var b = ActorSpawns.plan(SEED, i - 50, 7 - i, 1000 + i, ActorKind.DUST_DEVIL);
            assertEquals(a, b);
        }
    }

    @Test
    void eachWorldHasItsOwn() {
        var a = ActorSpawns.plan(SEED, 3, 4, 500, ActorKind.DUST_DEVIL);
        var b = ActorSpawns.plan(SEED + 1, 3, 4, 500, ActorKind.DUST_DEVIL);
        assertNotEquals(a.x(), b.x());
        assertNotEquals(a.startTick(), b.startTick());
    }

    @Test
    void aPlanIsInItsCellAndSlot() {
        for (int cx = -3; cx <= 3; cx++)
            for (int cz = -3; cz <= 3; cz++)
                for (long slot = -2; slot <= 2; slot++) {
                    var plan = ActorSpawns.plan(SEED, cx, cz, slot, ActorKind.DUST_DEVIL);
                    assertEquals(cx, ActorSpawns.cellOf(plan.x()));
                    assertEquals(cz, ActorSpawns.cellOf(plan.z()));
                    assertEquals(slot, ActorSpawns.slotOf(plan.startTick()));
                    assertTrue(plan.roll() >= 0F && plan.roll() < 1F);
                }
    }

    @Test
    void cellsAndSlotsCountDownBelowZero() {
        assertEquals(0, ActorSpawns.cellOf(0D));
        assertEquals(0, ActorSpawns.cellOf(ActorSpawns.CELL_BLOCKS - 0.5D));
        assertEquals(-1, ActorSpawns.cellOf(-0.5D));
        assertEquals(-1, ActorSpawns.cellOf(-ActorSpawns.CELL_BLOCKS));
        assertEquals(-1, ActorSpawns.slotOf(-1));
        assertEquals(1, ActorSpawns.slotOf(ActorSpawns.SLOT_TICKS));
    }

    @Test
    void neighboringChancesHaveTheirOwnKeysAndRolls() {
        var keys = new HashSet<Long>();
        var rolls = new HashSet<Float>();
        for (int cx = -5; cx <= 5; cx++)
            for (int cz = -5; cz <= 5; cz++)
                for (long slot = 0; slot < 5; slot++) {
                    var plan = ActorSpawns.plan(SEED, cx, cz, slot, ActorKind.DUST_DEVIL);
                    keys.add(plan.key());
                    rolls.add(plan.roll());
                }
        assertEquals(11 * 11 * 5, keys.size());
        // Spread, not stuck on a few values
        assertTrue(rolls.size() > 11 * 11 * 5 * 0.95, "rolls " + rolls.size());
    }

    @Test
    void rollsAreSpreadEvenly() {
        // About the biome's chance of them fall under it
        int under = 0, total = 0;
        for (int cx = 0; cx < 40; cx++)
            for (long slot = 0; slot < 50; slot++, total++)
                if (ActorSpawns.plan(SEED, cx, 0, slot, ActorKind.DUST_DEVIL).roll() < 0.1F)
                    under++;
        assertEquals(0.1D, under / (double) total, 0.02D);
    }
}
