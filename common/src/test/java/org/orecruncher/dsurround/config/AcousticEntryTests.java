// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AcousticEntryTests {

    private static final ISoundFactory SOUND = Fakes.of(ISoundFactory.class,
            Map.of("getLocation", args -> Identifier.fromNamespaceAndPath("test", "sound")));

    private static IConditionEvaluator answering(boolean result, int[] calls) {
        return Fakes.of(IConditionEvaluator.class, Map.of("check", args -> {
            calls[0]++;
            return result;
        }));
    }

    @Test
    void noConditionAlwaysMatchesWithoutRunningAScript() {
        var calls = new int[1];
        var entry = new AcousticEntry(SOUND, null, answering(false, calls));
        assertTrue(entry.matches());
        assertEquals(0, calls[0]);
    }

    @Test
    void aConditionIsCheckedByTheGivenEvaluator() {
        var calls = new int[1];
        var condition = new Script("weather.isRaining()");
        assertTrue(new AcousticEntry(SOUND, condition, answering(true, calls)).matches());
        assertFalse(new AcousticEntry(SOUND, condition, answering(false, calls)).matches());
        assertEquals(2, calls[0]);
    }

    @Test
    void entriesWithTheSameSoundAndConditionAreEqual() {
        // How duplicates are found; the evaluator isn't part of it
        var calls = new int[1];
        var a = new AcousticEntry(SOUND, new Script("true"), answering(true, calls));
        var b = new AcousticEntry(SOUND, new Script("true"), answering(false, calls));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private static AcousticEntry withVolume(Object result, int[] calls) {
        IConditionEvaluator evaluator = Fakes.of(IConditionEvaluator.class, Map.of("eval", args -> {
            calls[0]++;
            return result;
        }));
        return new AcousticEntry(SOUND, null, WeightValue.of(10), new Script("lib.smoothStep(-4, 4, player.getY())"), evaluator);
    }

    @Test
    void noVolumeIsFullWithoutRunningAScript() {
        var calls = new int[1];
        IConditionEvaluator evaluator = Fakes.of(IConditionEvaluator.class, Map.of("eval", args -> {
            calls[0]++;
            return 0.0;
        }));
        assertEquals(1F, new AcousticEntry(SOUND, null, evaluator).volume());
        assertEquals(0, calls[0]);
    }

    @Test
    void aVolumeScriptGivesTheVolumeHeldBetween0And1() {
        var calls = new int[1];
        assertEquals(0.25F, withVolume(0.25, calls).volume());
        assertEquals(1F, withVolume(3.0, calls).volume());
        assertEquals(0F, withVolume(-2.0, calls).volume());
        assertEquals(3, calls[0]);
    }

    @Test
    void aVolumeScriptThatGivesNoNumberSilencesTheLoop() {
        var calls = new int[1];
        assertEquals(0F, withVolume("loud", calls).volume());
        assertEquals(0F, withVolume(Double.NaN, calls).volume());
        assertEquals(0F, withVolume(Double.POSITIVE_INFINITY, calls).volume());
        assertEquals(0F, withVolume(null, calls).volume());
    }

    @Test
    void entriesWithDifferentVolumesAreNotDuplicates() {
        var calls = new int[1];
        var full = new AcousticEntry(SOUND, null, answering(true, calls));
        assertNotEquals(full, withVolume(0.5, calls));
    }
}
