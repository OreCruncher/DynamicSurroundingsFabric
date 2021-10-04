package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Language;

@Environment(EnvType.CLIENT)
public final class Localization {

    public static String load(String key) {
        return Language.getInstance().get(key);
    }
}
