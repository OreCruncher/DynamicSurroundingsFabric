// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod's shaders as text, for tests that hold a shader's constants and layout to the Java that mirrors them
 */
public final class ShaderSources {

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s*<([a-z0-9_.-]+):([a-z0-9_./-]+)>", Pattern.MULTILINE);
    private static final Pattern CONDITION = Pattern.compile("^\\s*#(ifdef|ifndef|if|else|endif)\\b\\s*(\\w*)");
    private static final Pattern DEFINE_NAME = Pattern.compile("^\\s*#define\\s+(\\w+)");
    private static final Pattern DEFINE = Pattern.compile("^\\s*#define\\s+(\\w+)\\s+(\\S+)", Pattern.MULTILINE);
    private static final Pattern MEMBER = Pattern.compile("^\\s*(\\w+)\\s+(\\w+)\\s*(?:\\[([^\\]]+)])?\\s*;");

    private ShaderSources() {
    }

    /**
     * A shader's source
     *
     * @param path under the mod's shaders folder, such as {@code core/aurora.fsh}
     */
    public static String read(String path) throws IOException {
        try (var in = Objects.requireNonNull(ShaderSources.class.getResourceAsStream("/assets/dsurround/shaders/" + path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A number in a shader's source, the first group of {@code pattern}, which must match
     */
    public static float constant(String source, String pattern) {
        var match = Pattern.compile(pattern).matcher(source);
        assertTrue(match.find(), pattern);
        return Float.parseFloat(match.group(1));
    }

    /**
     * A shader program as the game compiles it: its includes put in (the mod's and the game's), and what its
     * {@code #ifdef}, {@code #ifndef} and {@code #else} leave out with the pipeline's defines dropped
     *
     * @param shader  a pipeline's shader id, such as {@code dsurround:core/rainbow}
     * @param defined the names the pipeline defines, flags and values both
     */
    public static String program(Identifier shader, String extension, Set<String> defined) throws IOException {
        var source = resource("/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + extension);
        return preprocess(includes(source), defined);
    }

    private static String includes(String source) throws IOException {
        var match = INCLUDE.matcher(source);
        var out = new StringBuilder();
        while (match.find()) {
            var included = resource("/assets/" + match.group(1) + "/shaders/include/" + match.group(2));
            match.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(includes(included)));
        }
        match.appendTail(out);
        return out.toString();
    }

    private static String preprocess(String source, Set<String> given) {
        var defined = new java.util.HashSet<>(given);
        var out = new StringBuilder();
        // Whether each open condition keeps its lines, and whether the one it is in does
        var keeping = new ArrayDeque<Boolean>();
        boolean keep = true;
        int number = 0;
        for (var line : source.split("\n")) {
            number++;
            var match = CONDITION.matcher(line);
            if (match.find()) {
                switch (match.group(1)) {
                    case "ifdef", "ifndef" -> {
                        keeping.push(keep);
                        keep = keep && (defined.contains(match.group(2)) == match.group(1).equals("ifdef"));
                    }
                    // An expression isn't worked out: its lines are kept
                    case "if" -> keeping.push(keep);
                    case "else" -> keep = Boolean.TRUE.equals(keeping.peek()) && !keep;
                    default -> {
                        assertFalse(keeping.isEmpty(), "an #endif with no #if, line " + number);
                        keep = keeping.pop();
                    }
                }
                continue;
            }
            if (keep) {
                var define = DEFINE_NAME.matcher(line);
                if (define.find())
                    defined.add(define.group(1));
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ShaderSources.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The size in bytes std140 gives a uniform block in a shader's source: floats, vectors, a mat4, and arrays of
     * them (an array's elements each taking a vec4's room), their sizes numbers or names {@code #define}d in the source
     * or given
     */
    public static int blockSize(String source, String block, Map<String, String> defines) {
        var start = Pattern.compile("uniform\\s+" + block + "\\s*\\{").matcher(source);
        assertTrue(start.find(), "no uniform block " + block);
        int end = source.indexOf("};", start.end());
        var sizes = new java.util.HashMap<>(defines);
        var define = DEFINE.matcher(source);
        while (define.find())
            sizes.putIfAbsent(define.group(1), define.group(2));
        int offset = 0;
        for (var line : source.substring(start.end(), end).split("\n")) {
            var member = MEMBER.matcher(line.replaceAll("//.*", ""));
            if (!member.find())
                continue;
            int size = switch (member.group(1)) {
                case "float", "int", "uint", "bool" -> 4;
                case "vec2", "ivec2" -> 8;
                case "vec3", "ivec3" -> 12;
                case "vec4", "ivec4" -> 16;
                case "mat4" -> 64;
                default -> throw new AssertionError("a member of a type the test doesn't know: " + line);
            };
            int align = switch (size) {
                case 4 -> 4;
                case 8 -> 8;
                default -> 16;
            };
            if (member.group(3) != null) {
                int elements = count(member.group(3), sizes);
                int stride = Math.max(16, (size + 15) / 16 * 16);
                offset = (offset + 15) / 16 * 16 + stride * elements;
            } else {
                offset = (offset + align - 1) / align * align + size;
            }
        }
        return (offset + 15) / 16 * 16;
    }

    // An array's size: a number, a name, or two of them divided or multiplied, as GLSL works it out with whole numbers
    private static int count(String expression, Map<String, String> sizes) {
        var terms = expression.trim().split("\\s*[/*]\\s*");
        int value = term(terms[0], sizes);
        if (terms.length > 1)
            value = expression.contains("/") ? value / term(terms[1], sizes) : value * term(terms[1], sizes);
        return value;
    }

    private static int term(String term, Map<String, String> sizes) {
        return Integer.parseInt(sizes.getOrDefault(term, term).replaceAll("\\.0$", ""));
    }
}
