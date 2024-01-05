package org.orecruncher.dsurround.lib;


import net.minecraft.locale.Language;

import java.util.Optional;

public final class Localization {

    public static String load(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    public static Optional<String> loadIfPresent(String key) {
        var result = Language.getInstance().getOrDefault(key, null);
        return Optional.ofNullable(result);
    }
}
