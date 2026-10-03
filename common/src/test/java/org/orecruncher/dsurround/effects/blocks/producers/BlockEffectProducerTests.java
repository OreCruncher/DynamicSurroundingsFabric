package org.orecruncher.dsurround.effects.blocks.producers;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the chance roll shared by block effect producers, and the particle producer's handling of a missing
 * particle.
 */
public class BlockEffectProducerTests {

    private static final Script CHANCE = new Script("1");
    private static final Script CONDITIONS = new Script("true");

    private record FakeEvaluator(boolean conditions, Object chance) implements IConditionEvaluator {
        @Override
        public boolean check(Script conditions) {
            return this.conditions;
        }

        @Override
        public Object eval(Script conditions) {
            return this.chance;
        }
    }

    @Test
    void certainChanceAlwaysTriggers() {
        assertTrue(BlockEffectProducer.rollChance(1.0D, Randomizer.current()));
    }

    @Test
    void zeroChanceNeverTriggers() {
        for (int i = 0; i < 100; i++)
            assertFalse(BlockEffectProducer.rollChance(0.0D, Randomizer.current()));
    }

    @Test
    void anyNumberIsAChance() {
        // Regression: only a Double counted, so a script function returning an integer or a float never triggered
        assertTrue(BlockEffectProducer.rollChance(1, Randomizer.current()));
        assertTrue(BlockEffectProducer.rollChance(1.0F, Randomizer.current()));
        assertTrue(BlockEffectProducer.rollChance(1L, Randomizer.current()));
    }

    @Test
    void anythingElseNeverTriggers() {
        // A failed script evaluates to false
        assertFalse(BlockEffectProducer.rollChance(false, Randomizer.current()));
        assertFalse(BlockEffectProducer.rollChance("1", Randomizer.current()));
        assertFalse(BlockEffectProducer.rollChance(null, Randomizer.current()));
    }

    @Test
    void missingParticleIsIgnored() {
        // Regression: a supplier whose vanilla fallback returned null passed it to the particle engine, which throws
        boolean[] asked = {false};
        var producer = new BlockParticleEffectProducer(new FakeEvaluator(true, 1.0D), CHANCE, CONDITIONS,
                (world, state, pos, rand) -> {
                    asked[0] = true;
                    return null;
                });

        var result = assertDoesNotThrow(() -> producer.produce(null, null, null, Randomizer.current()));

        assertTrue(asked[0]);
        assertTrue(result.isEmpty());
    }

    @Test
    void falseConditionsSkipTheParticle() {
        var producer = new BlockParticleEffectProducer(new FakeEvaluator(false, 1.0D), CHANCE, CONDITIONS,
                (world, state, pos, rand) -> fail("conditions are false"));

        assertTrue(producer.produce(null, null, null, Randomizer.current()).isEmpty());
    }
}
