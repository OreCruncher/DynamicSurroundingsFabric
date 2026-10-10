package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.scripting.Script;
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
}
