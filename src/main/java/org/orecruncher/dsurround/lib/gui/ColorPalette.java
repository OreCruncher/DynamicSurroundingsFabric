package org.orecruncher.dsurround.lib.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public final class ColorPalette {
    // Branding colors
    public static final TextColor CURSEFORGE = of("#f16436");
    public static final TextColor MODRINTH = of("#1bd96a");

    // Minecraft colors mapped to codes
    public static final TextColor MC_BLACK = of(Formatting.BLACK);
    public static final TextColor MC_DARKBLUE = of(Formatting.DARK_BLUE);
    public static final TextColor MC_DARKGREEN = of(Formatting.DARK_GREEN);
    public static final TextColor MC_DARKAQUA = of(Formatting.DARK_AQUA);
    public static final TextColor MC_DARKRED = of(Formatting.DARK_RED);
    public static final TextColor MC_DARKPURPLE = of(Formatting.DARK_PURPLE);
    public static final TextColor MC_GOLD = of(Formatting.GOLD);
    public static final TextColor MC_GRAY = of(Formatting.GRAY);
    public static final TextColor MC_DARKGRAY = of(Formatting.DARK_GRAY);
    public static final TextColor MC_BLUE = of(Formatting.BLUE);
    public static final TextColor MC_GREEN = of(Formatting.GREEN);
    public static final TextColor MC_AQUA = of(Formatting.AQUA);
    public static final TextColor MC_RED = of(Formatting.RED);
    public static final TextColor MC_LIGHTPURPLE = of(Formatting.LIGHT_PURPLE);
    public static final TextColor MC_YELLOW = of(Formatting.YELLOW);
    public static final TextColor MC_WHITE = of(Formatting.WHITE);

    public static final TextColor CRIMSON = of("#8D230F");

    public static final TextColor RED = of(255, 0, 0);
    public static final TextColor ORANGE = of(255, 127, 0);
    public static final TextColor YELLOW = of(255, 255, 0);
    public static final TextColor LGREEN = of(127, 255, 0);
    public static final TextColor GREEN = of(0, 255, 0);
    public static final TextColor TURQOISE = of(0, 255, 127);
    public static final TextColor CYAN = of(0, 255, 255);
    public static final TextColor AUQUAMARINE = of(0, 127, 255);
    public static final TextColor BLUE = of(0, 0, 255);
    public static final TextColor VIOLET = of(127, 0, 255);
    public static final TextColor MAGENTA = of(255, 0, 255);
    public static final TextColor RASPBERRY = of(255, 0, 127);
    public static final TextColor BLACK = of(0, 0, 0);
    public static final TextColor WHITE = of(255, 255, 255);
    public static final TextColor PURPLE = of(80, 0, 80);
    public static final TextColor INDIGO = of(75, 0, 130);
    public static final TextColor NAVY = of(0, 0, 128);
    public static final TextColor TAN = of(210, 180, 140);
    public static final TextColor GOLD = of(255, 215, 0);
    public static final TextColor GRAY = of(128, 128, 128);
    public static final TextColor LGRAY = of(192, 192, 192);
    public static final TextColor SLATEGRAY = of(112, 128, 144);
    public static final TextColor DARKSLATEGRAY = of(47, 79, 79);

    private static TextColor of(Formatting formatColor) {
        return TextColor.fromFormatting(formatColor);
    }

    private static TextColor of(String formatColor) {
        return TextColor.parse(formatColor).getOrThrow(false, (msg)-> {});
    }

    private static TextColor of(int red, int green, int blue) {
        var rgb = ((red & 0xFF) << 16) |
                  ((green & 0xFF) << 8)  |
                  ((blue & 0xFF));
        return TextColor.fromRgb(rgb);
    }
}