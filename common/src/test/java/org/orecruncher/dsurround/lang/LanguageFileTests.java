package org.orecruncher.dsurround.lang;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks every language file against en_us, the source the translations are made from (by Crowdin). Each file is
 * found by listing the lang folder, so a new language is checked without changing these tests. A broken
 * translation then fails the build rather than the game.
 * <p>
 * Translations may leave keys out (Minecraft shows the English text), but may not have keys en_us doesn't, and
 * what they do have must keep the source's placeholders and markup.
 */
public class LanguageFileTests {

    // A format placeholder: %s, %d, %1$s and so on, or %% for a literal percent
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z%])");
    // Minecraft language codes: lowercase, e.g. en_us, zh_cn, es_mx
    private static final Pattern FILE_NAME = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,4}");

    /**
     * The arguments a text uses, by position ("%s" is the next one in order), plus "%" for each literal percent.
     * Translations may change the order, so this is a set of positions rather than a sequence.
     */
    static TreeSet<String> placeholders(String text) {
        var result = new TreeSet<String>();
        int next = 1;
        var matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                result.add("%%");
            } else if (matcher.group(1) != null) {
                result.add(matcher.group(1) + "$" + matcher.group(2));
            } else {
                result.add(next++ + "$" + matcher.group(2));
            }
        }
        return result;
    }

    /**
     * The markdown structure of a text: how many color tags open and close, and how many links it has. A
     * translation must keep these, though it changes the words.
     */
    static String markup(String text) {
        return "<color> " + count(text, "<color:") + ", </color> " + count(text, "</color>") + ", links " + count(text, "](");
    }

    private static int count(String text, String fragment) {
        int n = 0;
        for (int i = text.indexOf(fragment); i >= 0; i = text.indexOf(fragment, i + fragment.length()))
            n++;
        return n;
    }

    // ---- The source ------------------------------------------------------------------------------------------

    @Test
    void sourceAndTranslationsAreFound() {
        var names = LanguageFiles.names();

        assertEquals(LanguageFiles.SOURCE, names.getFirst());
        assertFalse(LanguageFiles.translations().isEmpty(), "no translations found in " + LanguageFiles.folder());
    }

    @Test
    void sourceIsWellFormed() {
        // Duplicate keys and non-string values throw
        var source = LanguageFiles.read(LanguageFiles.SOURCE);

        for (var entry : source.entrySet())
            assertFalse(entry.getValue().isBlank(), "en_us: '" + entry.getKey() + "' is blank");
    }

    // ---- Each translation ------------------------------------------------------------------------------------

    @TestFactory
    Stream<DynamicTest> translations() {
        var source = LanguageFiles.read(LanguageFiles.SOURCE);
        return LanguageFiles.translations().stream().flatMap(name -> Stream.of(
                DynamicTest.dynamicTest(name + ": file name is a Minecraft language code", () ->
                        // Minecraft resource paths must be lowercase; Crowdin gives "pl_PL" without a language mapping
                        assertTrue(FILE_NAME.matcher(name).matches(),
                                name + ".json: not a lowercase Minecraft language code; check languages_mapping in crowdin.yml")),
                DynamicTest.dynamicTest(name + ": only keys that en_us has", () -> {
                    var stale = LanguageFiles.read(name).keySet().stream().filter(k -> !source.containsKey(k)).toList();
                    assertTrue(stale.isEmpty(), name + ": keys en_us doesn't have: " + stale);
                }),
                DynamicTest.dynamicTest(name + ": no blank values", () ->
                        // Crowdin can export a skipped string as "" in some formats; that would show as nothing
                        check(name, source, (key, english, text) -> text.isBlank() ? "is blank" : null)),
                DynamicTest.dynamicTest(name + ": same placeholders as en_us", () ->
                        check(name, source, (key, english, text) -> placeholders(text).equals(placeholders(english))
                                ? null
                                : "has placeholders " + placeholders(text) + " but en_us has " + placeholders(english))),
                DynamicTest.dynamicTest(name + ": same markup as en_us", () ->
                        check(name, source, (key, english, text) -> markup(text).equals(markup(english))
                                ? null
                                : "has " + markup(text) + " but en_us has " + markup(english)))));
    }

    private interface EntryCheck {
        /**
         * What is wrong with the translation of {@code key}, or null if nothing is.
         */
        String problem(String key, String english, String text);
    }

    /**
     * Checks each of the translation's entries that en_us also has, failing with every problem found.
     */
    private static void check(String name, Map<String, String> source, EntryCheck check) {
        var problems = new ArrayList<String>();
        for (var entry : LanguageFiles.read(name).entrySet()) {
            var english = source.get(entry.getKey());
            if (english == null)
                continue; // reported by the stale key test
            var problem = check.problem(entry.getKey(), english, entry.getValue());
            if (problem != null)
                problems.add("'" + entry.getKey() + "' " + problem);
        }
        assertTrue(problems.isEmpty(), name + ":\n  " + String.join("\n  ", problems));
    }

    // ---- The helpers -----------------------------------------------------------------------------------------

    @Test
    void placeholdersAreByPosition() {
        assertEquals(new TreeSet<>(List.of("1$s", "2$s")), placeholders("%s and %s"));
        assertEquals(placeholders("%1$s then %2$s"), placeholders("%2$s, before it %1$s"), "reordered is the same");
        assertEquals(new TreeSet<>(List.of("1$d", "%%")), placeholders("%d%%"));
        assertNotEquals(placeholders("%1$s"), placeholders("%1$s %2$s"));
    }

    @Test
    void markupCountsTagsAndLinks() {
        assertEquals("<color> 2, </color> 1, links 1", markup("<color:red>[a](https://x.com)</color> <color:#FFFFFF>b"));
    }
}
