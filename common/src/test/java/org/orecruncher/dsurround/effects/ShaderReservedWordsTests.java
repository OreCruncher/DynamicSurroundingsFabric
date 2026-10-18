package org.orecruncher.dsurround.effects;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mod's shaders must not use words GLSL reserves. NVIDIA's and AMD's drivers let most of them pass, but Mesa
 * (Linux) refuses them: "packed" as a variable name kept the ground fog from compiling there.
 */
public class ShaderReservedWordsTests {

    // Reserved by GLSL 1.50 for future use, and keywords of later versions a 1.50 compiler may know
    private static final Set<String> RESERVED = Set.of(
            "common", "partition", "active", "asm", "class", "union", "enum", "typedef", "template", "this", "packed",
            "resource", "goto", "inline", "noinline", "public", "static", "extern", "external", "interface", "long",
            "short", "half", "fixed", "unsigned", "superp", "input", "output", "hvec2", "hvec3", "hvec4", "fvec2",
            "fvec3", "fvec4", "sampler3DRect", "filter", "sizeof", "cast", "namespace", "using", "row_major", "precise",
            "patch", "sample", "subroutine", "buffer", "shared", "coherent", "volatile", "restrict", "readonly",
            "writeonly", "atomic_uint", "double", "dvec2", "dvec3", "dvec4", "dmat2", "dmat3", "dmat4");

    private static final Pattern WORD = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    @Test
    void noShaderUsesAReservedWord() throws Exception {
        // Anchored on a file of the mod's own: other mods' assets folders are on the class path too
        var lang = Objects.requireNonNull(getClass().getResource("/assets/dsurround/lang/en_us.json"));
        var shaders = Path.of(lang.toURI()).getParent().getParent().resolve("shaders");

        var problems = new ArrayList<String>();
        int files;
        try (var paths = Files.walk(shaders)) {
            var sources = paths.filter(p -> p.toString().matches(".*\\.(fsh|vsh|glsl)$")).toList();
            files = sources.size();
            for (var source : sources)
                check(source, problems);
        }
        assertTrue(files > 10, "found only " + files + " shader files under " + shaders);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void check(Path source, ArrayList<String> problems) throws IOException {
        var text = Files.readString(source, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", " ");
        var lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            var code = lines[i].replaceAll("//.*", "");
            var words = WORD.matcher(code);
            while (words.find())
                if (RESERVED.contains(words.group()))
                    problems.add("%s:%d uses '%s'".formatted(source.getFileName(), i + 1, words.group()));
        }
    }
}
