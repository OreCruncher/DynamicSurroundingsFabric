package org.orecruncher.dsurround.effects.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.entity.HealthPopoffEffect.Change;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what a health change pops off, how a pop-off moves, and that every crit word has a translation.
 */
public class HealthPopoffTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- What shows ------------------------------------------------------------------------------------------

    @Test
    void damageShowsAsAWholeNumber() {
        var change = Change.of(-4.5F, 20F);
        assertEquals(4, change.amount());
        assertFalse(change.heal());
        assertFalse(change.critical());
    }

    @Test
    void healingShowsAsHealing() {
        var change = Change.of(1F, 20F);
        assertEquals(1, change.amount());
        assertTrue(change.heal());
        assertFalse(change.critical());
    }

    @Test
    void aFractionShowsAsOne() {
        // Less than a point of damage rounds to nothing, but something did happen
        var damage = Change.of(-0.5F, 20F);
        assertEquals(1, damage.amount());
        assertFalse(damage.heal());
        var heal = Change.of(0.25F, 20F);
        assertEquals(1, heal.amount());
        assertTrue(heal.heal());
    }

    @Test
    void aHeavyBlowIsCritical() {
        // 40% of a zombie's 20
        assertTrue(Change.of(-8F, 20F).critical());
        assertFalse(Change.of(-7F, 20F).critical());
        // Healing never is
        assertFalse(Change.of(20F, 20F).critical());
    }

    @Test
    void healingInFractionsShowsOnceItComesToAPoint() {
        // A full stomach heals a sixth of a point at a time, twice a second
        var tally = new HealthPopoffEffect.Tally();
        for (int i = 0; i < 5; i++)
            assertNull(tally.add(1F / 6F, 20F), "shown at sip " + i);
        var shown = tally.add(1F / 6F, 20F);
        assertNotNull(shown);
        assertEquals(1, shown.amount());
        assertTrue(shown.heal());
        // Started afresh
        assertNull(tally.add(1F / 6F, 20F));
    }

    @Test
    void healingShowsItsWholePointsAndKeepsTheRest() {
        var tally = new HealthPopoffEffect.Tally();
        assertEquals(3, Objects.requireNonNull(tally.add(3.5F, 20F)).amount());
        // The half left over makes the next half a point
        assertEquals(1, Objects.requireNonNull(tally.add(0.5F, 20F)).amount());
    }

    @Test
    void aBlowShowsAtOnceAndForgetsHealingSoFar() {
        var tally = new HealthPopoffEffect.Tally();
        assertNull(tally.add(0.75F, 20F));
        var blow = tally.add(-0.5F, 20F);
        assertNotNull(blow);
        assertEquals(1, blow.amount());
        assertFalse(blow.heal());
        assertNull(tally.add(0.5F, 20F));
    }

    // ---- Motion ----------------------------------------------------------------------------------------------

    @Test
    void itJumpsUpThenFallsBackPastTheStart() {
        assertEquals(0F, HealthPopoffs.heightAt(0F));
        float top = HealthPopoffs.RISE / HealthPopoffs.GRAVITY;
        assertTrue(HealthPopoffs.heightAt(top) > HealthPopoffs.heightAt(1F));
        assertTrue(HealthPopoffs.heightAt(HealthPopoffs.LIFE) < 0F);
    }

    @Test
    void itGrowsQuicklyThenShrinks() {
        assertEquals(1F, HealthPopoffs.sizeAt(0F));
        assertEquals(HealthPopoffs.PEAK, HealthPopoffs.sizeAt(HealthPopoffs.GROW), 1.0E-5F);
        assertTrue(HealthPopoffs.sizeAt(8F) < HealthPopoffs.PEAK);
        assertEquals(HealthPopoffs.END, HealthPopoffs.sizeAt(HealthPopoffs.LIFE), 1.0E-5F);
    }

    @Test
    void itFadesOnlyAtTheEnd() {
        assertEquals(1F, HealthPopoffs.alphaAt(0F));
        assertEquals(1F, HealthPopoffs.alphaAt(HealthPopoffs.LIFE * (1F - HealthPopoffs.FADE)));
        float last = HealthPopoffs.alphaAt(HealthPopoffs.LIFE);
        // Still above what the text shader drops
        assertTrue(last > 0.1F && last < 0.5F);
    }

    // ---- Crit words ------------------------------------------------------------------------------------------

    @Test
    void everyCritWordIsTranslated() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/assets/dsurround/lang/en_us.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject lang = JsonParser.parseReader(reader).getAsJsonObject();
            for (int i = 0; i < HealthPopoffEffect.CRIT_WORDS; i++)
                assertTrue(lang.has(HealthPopoffEffect.CRIT_WORD_PREFIX + i), "no crit word " + i);
            assertFalse(lang.has(HealthPopoffEffect.CRIT_WORD_PREFIX + HealthPopoffEffect.CRIT_WORDS),
                    "more crit words than CRIT_WORDS");
        }
    }
}
