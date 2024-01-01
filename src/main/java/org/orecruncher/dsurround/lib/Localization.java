package org.orecruncher.dsurround.lib;


import net.minecraft.locale.Language;

public final class Localization {

    public static String load(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
