package org.orecruncher.dsurround.profiling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redacts the player's home folder's path in a recording file: every place it is written, the account's name in it
 * starred out: as the system writes it, with forward slashes, and as in a file URL. Most of what is private is kept out
 * of a recording by leaving out the events that carry it (see {@link ProfileRecording#PRIVATE}), but some is shared
 * between events, where leaving events out can't reach it: where a module's or class's code was loaded from, say,
 * which is a path in the player's folders.
 * <p>
 * Done on the file's bytes, in place, without changing its length: Java Flight Recorder writes text as UTF-8,
 * Latin-1, or each character as a variable-length number, and each character is swapped for one that takes as many
 * bytes in each, so the file reads as it did. A character of the name outside the Basic Multilingual Plane is left (it
 * can't be swapped so), though the rest of the name is starred.
 */
final class Redaction {

    // Stand-ins for a character of the name, taking as many bytes as it does in each of JFR's encodings (UTF-8, and a
    // variable-length number of seven bits a byte): ASCII (one and one); up to U+07FF (two and two); up to U+3FFF
    // (three and two); the rest of the Basic Multilingual Plane (three and three)
    private static final char ONE_BYTE = '*';
    private static final char TWO_BYTES = (char) 0x00D7;
    private static final char THREE_TWO_BYTES = (char) 0x2217;
    private static final char THREE_BYTES = (char) 0xFF0A;

    private Redaction() {
    }

    /**
     * Redacts the home folder, as the forms of it {@link #replacements} gives, wherever it is in {@code file}
     */
    static void redact(Path file, String home) throws IOException {
        var replacements = replacements(home);
        if (replacements.isEmpty())
            return;
        var data = Files.readAllBytes(file);
        if (redact(data, replacements) > 0)
            Files.write(file, data);
    }

    /**
     * The home folder as it may be written, each with what it is redacted to: as the system writes it, with forward
     * slashes, and with spaces as in a URL; the last part of it (the account's name) starred out
     */
    static Map<String, String> replacements(String home) {
        var forms = new LinkedHashMap<String, String>();
        if (home == null)
            return forms;
        var trimmed = home.replaceAll("[\\\\/]+$", "");
        int cut = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\')) + 1;
        var name = trimmed.substring(cut);
        // Too short to stand out: a home folder this short (a root, "/u") names no one
        if (name.length() < 2)
            return forms;
        var starred = trimmed.substring(0, cut) + star(name);
        forms.put(trimmed, starred);
        forms.put(trimmed.replace('\\', '/'), starred.replace('\\', '/'));
        if (trimmed.contains(" "))
            forms.put(trimmed.replace('\\', '/').replace(" ", "%20"), starred.replace('\\', '/').replace(" ", "%20"));
        return forms;
    }

    /**
     * Each character of {@code name} as one that takes as many bytes in each of JFR's encodings
     */
    static String star(String name) {
        var out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x80)
                out.append(c == ' ' ? ' ' : ONE_BYTE);
            else if (c < 0x800)
                out.append(TWO_BYTES);
            else if (c < 0x4000)
                out.append(THREE_TWO_BYTES);
            else if (!Character.isSurrogate(c))
                out.append(THREE_BYTES);
            else
                out.append(c);
        }
        return out.toString();
    }

    /**
     * Redacts every form in {@code data}, in each of JFR's encodings
     *
     * @return how many places were redacted
     */
    static int redact(byte[] data, Map<String, String> replacements) {
        int count = 0;
        for (var entry : replacements.entrySet()) {
            count += replace(data, utf8(entry.getKey()), utf8(entry.getValue()));
            count += replace(data, varints(entry.getKey()), varints(entry.getValue()));
            var latin = latin1(entry.getKey());
            var latinStars = latin1(entry.getValue());
            if (latin != null && latinStars != null)
                count += replace(data, latin, latinStars);
        }
        return count;
    }

    private static int replace(byte[] data, byte[] find, byte[] with) {
        if (find.length == 0 || find.length != with.length)
            return 0;
        int count = 0;
        outer:
        for (int i = 0; i + find.length <= data.length; i++) {
            for (int j = 0; j < find.length; j++)
                if (data[i + j] != find[j])
                    continue outer;
            System.arraycopy(with, 0, data, i, with.length);
            count++;
            i += find.length - 1;
        }
        return count;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Latin-1, if every character is in it
    private static byte[] latin1(String text) {
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) > 0xFF)
                return null;
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    // Each character as JFR writes it in a character array: a variable-length number, seven bits a byte
    private static byte[] varints(String text) {
        var out = new ByteArrayOutputStream(text.length());
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            while (c >= 0x80) {
                out.write((c & 0x7F) | 0x80);
                c >>>= 7;
            }
            out.write(c);
        }
        return out.toByteArray();
    }
}
