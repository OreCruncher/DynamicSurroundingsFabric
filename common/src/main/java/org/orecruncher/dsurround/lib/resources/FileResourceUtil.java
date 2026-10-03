package org.orecruncher.dsurround.lib.resources;

import java.nio.charset.Charset;
import java.util.Optional;

public final class FileResourceUtil {

    public static Optional<String> readResourceFromJar(String resourceName) {
        try {
            try (var inputStream = FileResourceUtil.class.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    return Optional.empty();
                }
                var assetBytes = inputStream.readAllBytes();
                var assetString = new String(assetBytes, Charset.defaultCharset());
                return Optional.of(assetString);
            }
        } catch(Throwable ignored) {}
        return Optional.empty();
    }
}
