package org.orecruncher.dsurround.lib.resources;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class FileResourceUtil {

    public static Optional<String> readResourceFromJar(String resourceName) {
        try {
            try (var inputStream = FileResourceUtil.class.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    return Optional.empty();
                }
                var assetBytes = inputStream.readAllBytes();
                var assetString = new String(assetBytes, StandardCharsets.UTF_8);
                return Optional.of(assetString);
            }
        } catch(Throwable ignored) {}
        return Optional.empty();
    }
}
