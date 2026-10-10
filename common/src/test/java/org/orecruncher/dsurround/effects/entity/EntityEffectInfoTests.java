package org.orecruncher.dsurround.effects.entity;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.IEntityEffect;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that one failing entity effect doesn't stop the others.
 */
public class EntityEffectInfoTests {

    private final List<String> ran = new ArrayList<>();
    private final List<IEntityEffect> failed = new ArrayList<>();

    private IEntityEffect effect(String name) {
        return new IEntityEffect() {
            @Override
            public void deactivate(EntityEffectInfo manager) {
                EntityEffectInfoTests.this.ran.add(name);
            }
        };
    }

    private IEntityEffect throwing(RuntimeException ex) {
        return new IEntityEffect() {
            @Override
            public void deactivate(EntityEffectInfo manager) {
                throw ex;
            }
        };
    }

    private EntityEffectInfo info(IEntityEffect... effects) {
        return new EntityEffectInfo(1, null, List.of(effects), (effect, t) -> this.failed.add(effect));
    }

    @Test
    void everyEffectRuns() {
        info(effect("a"), effect("b")).deactivate();

        assertEquals(List.of("a", "b"), this.ran);
        assertTrue(this.failed.isEmpty());
    }

    @Test
    void aFailingEffectIsReportedAndTheRestStillRun() {
        // Regression: an effect that threw ended the entity loop and, through it, every handler after the entity
        // effect handler for that tick (biome sounds, block effects, step accents, fog)
        var bad = throwing(new ClassCastException("not a player"));

        info(effect("a"), bad, effect("c")).deactivate();

        assertEquals(List.of("a", "c"), this.ran);
        assertEquals(List.of(bad), this.failed);
    }

    @Test
    void theFailureHandlerGetsTheException() {
        var boom = new IllegalStateException("boom");
        var seen = new ArrayList<Throwable>();
        var info = new EntityEffectInfo(1, null, List.of(throwing(boom)), (effect, t) -> seen.add(t));

        info.deactivate();

        assertEquals(List.of(boom), seen);
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        var info = info(effect("a"), new IEntityEffect() {
            @Override
            public void deactivate(EntityEffectInfo manager) {
                throw new OutOfMemoryError("pretend");
            }
        }, effect("c"));

        assertThrows(OutOfMemoryError.class, info::deactivate);
        assertEquals(List.of("a"), this.ran);
        assertTrue(this.failed.isEmpty());
    }

    @Test
    void eachInfoHasItsOwnEffects() {
        // Effects keep per-entity state in their fields, which only works because they are not shared
        var first = info(effect("a"));
        var second = info(effect("b"));

        assertNotSame(first.getEffects(), second.getEffects());
    }
}
