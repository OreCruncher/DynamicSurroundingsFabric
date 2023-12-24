package org.orecruncher.dsurround.lib;

import net.minecraft.util.Language;

public final class Localization {

    public static String load(String key) {
        return Language.getInstance().get(key);
    }
}
