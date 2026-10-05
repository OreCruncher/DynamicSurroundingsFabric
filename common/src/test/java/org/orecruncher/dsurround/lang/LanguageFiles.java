package org.orecruncher.dsurround.lang;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The mod's language files, found by listing the lang folder so a language added by Crowdin is tested without
 * changing any test.
 */
public final class LanguageFiles {

    public static final String SOURCE = "en_us";
    private static final String LANG_FOLDER = "/assets/dsurround/lang/";

    private LanguageFiles() {
    }

    /**
     * The folder holding the language files, from the test classpath.
     */
    public static Path folder() {
        var url = LanguageFiles.class.getResource(LANG_FOLDER + SOURCE + ".json");
        if (url == null)
            throw new IllegalStateException(SOURCE + ".json isn't on the test classpath");
        try {
            return Path.of(url.toURI()).getParent();
        } catch (URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("The language files aren't in a folder: " + url, e);
        }
    }

    /**
     * The names of all language files, e.g. "en_us", "pl_pl", sorted, with the source first.
     */
    public static List<String> names() {
        try (var files = Files.list(folder())) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted((a, b) -> a.equals(SOURCE) ? -1 : b.equals(SOURCE) ? 1 : a.compareTo(b))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The names of the translations: every language but the source.
     */
    public static List<String> translations() {
        return names().stream().filter(n -> !n.equals(SOURCE)).toList();
    }

    /**
     * The entries of a language file, in file order.
     *
     * @throws IllegalStateException if a key appears twice (a JSON parser would silently keep the last), or a value
     *                               isn't a string
     */
    public static Map<String, String> read(String name) {
        var path = folder().resolve(name + ".json");
        var entries = new LinkedHashMap<String, String>();
        try (var reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                var key = reader.nextName();
                if (reader.peek() != JsonToken.STRING)
                    throw new IllegalStateException(name + ": '" + key + "' isn't a string");
                if (entries.put(key, reader.nextString()) != null)
                    throw new IllegalStateException(name + ": '" + key + "' appears more than once");
            }
            reader.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    /**
     * A language as a lookup from key to text, as Minecraft does it: a key the language doesn't have uses the
     * English text, and a key neither has is shown as itself.
     */
    public static Function<String, String> lookup(String name) {
        var entries = new LinkedHashMap<>(read(SOURCE));
        entries.putAll(read(name));
        return key -> entries.getOrDefault(key, key);
    }
}
