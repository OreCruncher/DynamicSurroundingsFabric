package org.orecruncher.dsurround.config.libraries.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the helpers the libraries share: the versioned cache behind DimensionInformation, and the rule guard
 * that keeps one broken rule from stopping the others.
 */
public class LibraryHelpersTests {

    // ---- VersionedCache --------------------------------------------------------------------------------------

    private final AtomicInteger computed = new AtomicInteger();

    private String compute(Object key) {
        return key + "#" + this.computed.incrementAndGet();
    }

    @Test
    void cachedWhileKeyAndVersionStayTheSame() {
        var cache = new VersionedCache<Object, String>();
        var level = new Object();

        var first = cache.get(level, 1, this::compute);
        var second = cache.get(level, 1, this::compute);

        assertSame(first, second);
        assertEquals(1, this.computed.get());
    }

    @Test
    void rebuiltForAnotherLevel() {
        // Changing dimension, or joining another world, gives a new level
        var cache = new VersionedCache<Object, String>();
        cache.get(new Object(), 1, this::compute);

        cache.get(new Object(), 1, this::compute);

        assertEquals(2, this.computed.get());
    }

    @Test
    void rebuiltAfterTheLibraryChanges() {
        // Regression: the cached info was only reset by a reload handler, which ran before the library itself
        // reloaded; anything asking in between cached the old data until the next level load
        var cache = new VersionedCache<Object, String>();
        var level = new Object();
        cache.get(level, 1, this::compute);

        var afterReload = cache.get(level, 2, this::compute);

        assertEquals(2, this.computed.get());
        assertTrue(afterReload.endsWith("#2"));
    }

    @Test
    void levelsAreComparedByIdentity() {
        // Two levels are never the same level because they compare equal
        var cache = new VersionedCache<String, String>();
        cache.get(new String("overworld"), 1, this::compute);

        cache.get(new String("overworld"), 1, this::compute);

        assertEquals(2, this.computed.get());
    }

    @Test
    void clearForgetsTheValue() {
        var cache = new VersionedCache<Object, String>();
        var level = new Object();
        cache.get(level, 1, this::compute);

        cache.clear();
        cache.get(level, 1, this::compute);

        assertEquals(2, this.computed.get());
    }

    // ---- RuleGuard -------------------------------------------------------------------------------------------

    @Test
    void everyRuleIsApplied() {
        var applied = new ArrayList<String>();

        RuleGuard.forEach(List.of("a", "b", "c"), applied::add, (rule, t) -> fail("no failures expected"));

        assertEquals(List.of("a", "b", "c"), applied);
    }

    @Test
    void aFailingRuleIsReportedAndTheRestStillApply() {
        // Regression: in the block and entity effect libraries, one throwing rule stopped the rest (and, while
        // seeding the block cache, the remaining blocks)
        var applied = new ArrayList<String>();
        var failures = new ArrayList<String>();
        var boom = new IllegalStateException("boom");

        RuleGuard.forEach(List.of("a", "bad", "c"),
                rule -> {
                    if (rule.equals("bad"))
                        throw boom;
                    applied.add(rule);
                },
                (rule, t) -> {
                    assertSame(boom, t);
                    failures.add(rule);
                });

        assertEquals(List.of("a", "c"), applied);
        assertEquals(List.of("bad"), failures);
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        var applied = new ArrayList<String>();

        assertThrows(OutOfMemoryError.class, () -> RuleGuard.forEach(List.of("a", "fatal", "c"),
                rule -> {
                    if (rule.equals("fatal"))
                        throw new OutOfMemoryError("pretend");
                    applied.add(rule);
                },
                (rule, t) -> fail("a fatal error is rethrown, not reported")));

        assertEquals(List.of("a"), applied);
    }
}
