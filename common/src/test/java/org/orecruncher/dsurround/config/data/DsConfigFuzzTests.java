// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.ConfigFuzz.Source;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.testing.ConfigFuzz.*;

/**
 * Feeds the decoding of the configuration files in {@code dsconfigs} (biomes, blocks, dimensions, sound factories,
 * sound mappings and tags) large numbers of hand-edited versions of the files the mod ships, and checks what must
 * hold for every one of them:
 * <ul>
 *     <li>Nothing escapes a codec as an exception. The loader catches it, but then the whole file is lost; a
 *     problem must come back as an error in the result, so only the entry it is in is affected.</li>
 *     <li>A bad entry costs at most itself: every other entry in the file decodes exactly as it did before.</li>
 * </ul>
 * Each case takes one entry of one shipped file and edits it: values replaced with odd ones (wrong types, NaN, huge
 * numbers, deep nesting), strings garbled (block state specifications, scripts, ids, colours), members removed,
 * unknown ones added, values wrapped in arrays.
 * <p>
 * Runs are reproducible. Case {@code i} always uses {@code new Random(seed + i)}. When a case fails, the edited entry
 * is shrunk to a minimal one that still fails and printed as JSON, ready to paste into a regression test in
 * {@link ConfigDataTests}. To explore further, change {@link #CASES} and {@link #SEED} (or pass
 * {@code -Ddsconfig.fuzz.cases} and {@code -Ddsconfig.fuzz.seed}, if the build passes system properties to the test
 * JVM).
 */
class DsConfigFuzzTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int CASES = Integer.getInteger("dsconfig.fuzz.cases", 3_000);
    private static final long SEED = Long.getLong("dsconfig.fuzz.seed", 20261016L);

    private static final List<List<Source>> SOURCES_BY_KIND = loadSources();

    // ---- The tests -------------------------------------------------------------------------------------------

    @Test
    void editedEntriesStayInTheirEntry() {
        for (int i = 0; i < CASES; i++) {
            var random = new Random(SEED + i);
            var kind = pick(random, SOURCES_BY_KIND);
            var source = pick(random, kind);
            var entries = source.kind().entries().apply(source.root());
            assertNotNull(entries, source.path());
            if (entries.isEmpty())
                continue;

            int index = random.nextInt(entries.size());
            var edited = edit(random, entries.get(index));
            var problem = check(source, index, edited);
            if (problem != null) {
                var minimal = shrink(edited, candidate -> check(source, index, candidate) != null);
                fail("case %d (seed %d): %s, entry %d: %s%n  minimal entry: %s%n  minimal problem: %s%n  edited entry: %s"
                        .formatted(i, SEED, source.path(), index, problem, show(minimal), check(source, index, minimal), abbreviate(show(edited))));
            }
        }
    }

    @Test
    void wholeFilesOfTheWrongShapeDecodeWithoutThrowing() {
        // Not a list, or a list of odd things: nothing to keep, but nothing may escape either
        for (var kind : SOURCES_BY_KIND) {
            var codec = kind.getFirst().kind().codec();
            for (var odd : ODD_VALUES) {
                for (var root : List.of(lenient(odd), array(lenient(odd)), wrapValues(lenient(odd)))) {
                    var text = show(root);
                    assertDoesNotThrow(() -> codec.parse(JsonOps.INSTANCE, root), kind.getFirst().kind().name() + ": " + text);
                }
            }
        }
    }

    @Test
    void everyShippedFileIsCovered() {
        // A file the fuzz doesn't know how to read would be skipped silently
        int count = SOURCES_BY_KIND.stream().mapToInt(List::size).sum();
        assertTrue(count > 100, "only " + count + " files found");
        assertEquals(6, SOURCES_BY_KIND.size(), "a kind of file is missing");
    }

    @Test
    void decodingIsRepeatable() {
        // The isolation check compares entries by how they print, so that has to be stable
        for (var kind : SOURCES_BY_KIND)
            for (var source : kind)
                assertEquals(fingerprints(source, source.root()), fingerprints(source, source.root()), source.path());
    }

    private record Inner(@Nullable String value) {
    }

    private record Outer(List<Inner> list, Optional<Inner> optional) {
    }

    @Test
    void nullComponentsAreFoundAtAnyDepth() {
        assertNull(nullComponent(new Outer(List.of(new Inner("a")), Optional.of(new Inner("b"))), "entry"));
        assertEquals("entry.list[1].value", nullComponent(new Outer(List.of(new Inner("a"), new Inner(null)), Optional.empty()), "entry"));
        assertEquals("entry.optional.value", nullComponent(new Outer(List.of(), Optional.of(new Inner(null))), "entry"));
    }

    @Test
    void shrinkRemovesWhatDoesNotMatter() {
        var entry = JsonParser.parseString("{\"a\": 1, \"b\": [1, 2, {\"bad\": true}], \"c\": \"x\"}");
        var minimal = shrink(entry, e -> show(e).contains("bad"));
        assertEquals("{\"b\":[{\"bad\":true}]}", show(minimal));
    }

    // ---- The property ----------------------------------------------------------------------------------------

    /**
     * Decodes the file with entry {@code index} replaced by {@code edited}, and returns null if all is well,
     * otherwise a description of the problem
     */
    private static @Nullable String check(Source source, int index, JsonElement edited) {
        var root = source.root().deepCopy();
        var entries = source.kind().entries().apply(root);
        assert entries != null;
        entries.set(index, edited);

        Optional<?> decoded;
        try {
            decoded = source.kind().codec().parse(JsonOps.INSTANCE, root).resultOrPartial(ignored -> {
            });
        } catch (Throwable t) {
            return "decoding threw " + t;
        }

        // Whatever came through is whole
        if (decoded.isPresent()) {
            for (var entry : source.kind().decodedEntries().apply(decoded.get())) {
                var missing = nullComponent(entry, "entry");
                if (missing != null)
                    return missing + " decoded as null";
            }
        }

        // Every other entry as it was; the edited one gone, or there as whatever it now is
        var before = fingerprints(source, source.root());
        var after = decoded.map(d -> source.kind().decodedEntries().apply(d).stream().map(String::valueOf).toList()).orElse(List.of());
        var others = new ArrayList<>(before);
        others.remove(index);
        if (after.size() == before.size()) {
            var withoutEdited = new ArrayList<>(after);
            withoutEdited.remove(index);
            if (withoutEdited.equals(others))
                return null;
        } else if (after.equals(others)) {
            return null;
        }
        return "the other entries didn't decode as before: %d of %d entries came through".formatted(after.size(), before.size());
    }

    /**
     * The first component of a decoded record, at any depth (through records, lists and optionals), that is null: a
     * codec that turned bad input into null instead of an error
     */
    private static @Nullable String nullComponent(@Nullable Object value, String path) {
        if (value == null)
            return path;
        if (value instanceof Optional<?> optional)
            return optional.map(v -> nullComponent(v, path)).orElse(null);
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                var missing = nullComponent(list.get(i), path + "[" + i + "]");
                if (missing != null)
                    return missing;
            }
            return null;
        }
        if (value instanceof Record record) {
            for (var component : record.getClass().getRecordComponents()) {
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    var missing = nullComponent(accessor.invoke(record), path + "." + component.getName());
                    if (missing != null)
                        return missing;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }
}
