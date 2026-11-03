// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.text;

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
