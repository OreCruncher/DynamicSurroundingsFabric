// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod's shaders as text, for tests that hold a shader's constants and layout to the Java that mirrors them
 */
public final class ShaderSources {

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
}
