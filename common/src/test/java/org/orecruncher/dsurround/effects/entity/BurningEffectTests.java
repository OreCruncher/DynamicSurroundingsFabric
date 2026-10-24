package org.orecruncher.dsurround.effects.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how much fire a burning entity gets: by its size, its distance, and its share of the budget a crowd
 * shares, and for the list of entities whose vanilla fire is left off.
 */
public class BurningEffectTests {

    static {
        // BurningEffect refers to fluid tags and particle classes
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Size ------------------------------------------------------------------------------------------------

    @Test
    void biggerBodiesGetMoreTongues() {
        float chicken = BurningEffect.tonguesFor(0.4F, 0.7F);
        float zombie = BurningEffect.tonguesFor(0.6F, 1.95F);
        float spider = BurningEffect.tonguesFor(1.4F, 0.9F);
        assertTrue(chicken < zombie);
        assertEquals(2.9F, zombie, 0.05F);
        assertTrue(spider > zombie);
    }

    @Test
    void tonguesAreKeptWithinLimits() {
        assertEquals(BurningEffect.MIN_TONGUES, BurningEffect.tonguesFor(0.1F, 0.1F));
        // A giant (3.6 by 12) would otherwise ask for over a hundred a tick
        assertEquals(BurningEffect.MAX_TONGUES, BurningEffect.tonguesFor(3.6F, 12F));
    }

    @Test
    void zombieTonguesAreLargerThanAFlameJets() {
        assertEquals(BurningEffect.ZOMBIE_SCALE, BurningEffect.scaleFor(0.6F), 1.0E-5F);
        assertTrue(BurningEffect.ZOMBIE_SCALE > 1F);
        assertTrue(BurningEffect.scaleFor(1.4F) > BurningEffect.ZOMBIE_SCALE);
        assertEquals(BurningEffect.MIN_SCALE, BurningEffect.scaleFor(0.05F));
        assertEquals(BurningEffect.MAX_SCALE, BurningEffect.scaleFor(10F));
    }

    @Test
    void aDyingBodyTipsOverAsVanillasDoes() {
        assertEquals(0F, BurningEffect.tipped(0));
        assertEquals(0F, BurningEffect.tipped(1));
        float early = BurningEffect.tipped(4);
        assertTrue(early > 0.4F && early < 1F, "tips fast at first");
        // Flat once (deathTime - 1) / 20 * 1.6 reaches 1, at 13.5
        assertTrue(BurningEffect.tipped(13) < 1F);
        assertEquals(1F, BurningEffect.tipped(14));
        assertEquals(1F, BurningEffect.tipped(20));
    }

    // ---- Distance --------------------------------------------------------------------------------------------

    @Test
    void fullFireNearbyThinningToTheRange() {
        assertEquals(1F, BurningEffect.distanceShare(0D, 24));
        assertEquals(1F, BurningEffect.distanceShare(8D * 8D, 24));
        float middle = BurningEffect.distanceShare(16D * 16D, 24);
        assertTrue(middle < 1F && middle > BurningEffect.FAR_SHARE);
        assertEquals(BurningEffect.FAR_SHARE, BurningEffect.distanceShare(24D * 24D, 24), 1.0E-5F);
        assertEquals(BurningEffect.FAR_SHARE, BurningEffect.distanceShare(40D * 40D, 24), 1.0E-5F);
    }

    @Test
    void aRangeInsideTheNearDistanceDoesNotThin() {
        assertEquals(1F, BurningEffect.distanceShare(10D * 10D, 6));
    }

    // ---- Rounding --------------------------------------------------------------------------------------------

    @Test
    void fractionsRoundUpByChance() {
        assertEquals(2, BurningEffect.roundRandomly(2.25F, 0.5F));
        assertEquals(3, BurningEffect.roundRandomly(2.25F, 0.2F));
        assertEquals(0, BurningEffect.roundRandomly(0.5F, 0.6F));
        assertEquals(1, BurningEffect.roundRandomly(0.5F, 0.4F));
        assertEquals(4, BurningEffect.roundRandomly(4F, 0F));
    }

    @Test
    void roundingAddsUpOverTicks() {
        int total = 0;
        for (int i = 0; i < 1000; i++)
            total += BurningEffect.roundRandomly(0.7F, (i + 0.5F) / 1000F);
        assertEquals(700, total);
    }

    // ---- Budget ----------------------------------------------------------------------------------------------

    @Test
    void underBudgetEveryoneGetsWhatTheyAsk() {
        var budget = new FlameBudget(100F);
        for (long tick = 0; tick < 3; tick++)
            for (int i = 0; i < 10; i++)
                assertEquals(3F, budget.share(tick, 3F));
    }

    @Test
    void aCrowdSharesTheBudgetEvenly() {
        var budget = new FlameBudget(100F);
        // First tick: the crowd isn't known yet, so it is only found as it asks
        for (int i = 0; i < 40; i++)
            budget.share(0, 5F);

        // From then on all 40 get the same share, the first as much as the last, and together the budget
        float first = budget.share(1, 5F);
        float total = first;
        for (int i = 1; i < 40; i++) {
            float share = budget.share(1, 5F);
            assertEquals(first, share, 1.0E-5F);
            total += share;
        }
        assertEquals(2.5F, first, 1.0E-5F);
        assertEquals(100F, total, 1.0E-3F);
    }

    @Test
    void theBudgetRecoversWhenTheCrowdIsGone() {
        var budget = new FlameBudget(100F);
        for (int i = 0; i < 40; i++)
            budget.share(0, 5F);
        // Nobody burned for a while, then one does
        assertEquals(5F, budget.share(50, 5F));
    }

    // ---- Vanilla fire left off -------------------------------------------------------------------------------

    @Test
    void onlyListedEntitiesLoseVanillaFire() {
        try {
            BurningEntities.add(17);
            assertTrue(BurningEntities.drawsFireOf(17));
            assertFalse(BurningEntities.drawsFireOf(18));
            assertEquals(1, BurningEntities.count());
        } finally {
            BurningEntities.remove(17);
        }
        assertFalse(BurningEntities.drawsFireOf(17));
        assertEquals(0, BurningEntities.count());
    }
}
