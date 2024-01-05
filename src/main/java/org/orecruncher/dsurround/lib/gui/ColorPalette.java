package org.orecruncher.dsurround.lib.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

@SuppressWarnings("unused")
public final class ColorPalette {
    // Branding colors
    public static final TextColor CURSEFORGE = of("#f16436");
    public static final TextColor MODRINTH = of("#1bd96a");

    // Minecraft colors mapped to codes
    public static final TextColor MC_BLACK = of(ChatFormatting.BLACK);
    public static final TextColor MC_DARKBLUE = of(ChatFormatting.DARK_BLUE);
    public static final TextColor MC_DARKGREEN = of(ChatFormatting.DARK_GREEN);
    public static final TextColor MC_DARKAQUA = of(ChatFormatting.DARK_AQUA);
    public static final TextColor MC_DARKRED = of(ChatFormatting.DARK_RED);
    public static final TextColor MC_DARKPURPLE = of(ChatFormatting.DARK_PURPLE);
    public static final TextColor MC_GOLD = of(ChatFormatting.GOLD);
    public static final TextColor MC_GRAY = of(ChatFormatting.GRAY);
    public static final TextColor MC_DARKGRAY = of(ChatFormatting.DARK_GRAY);
    public static final TextColor MC_BLUE = of(ChatFormatting.BLUE);
    public static final TextColor MC_GREEN = of(ChatFormatting.GREEN);
    public static final TextColor MC_AQUA = of(ChatFormatting.AQUA);
    public static final TextColor MC_RED = of(ChatFormatting.RED);
    public static final TextColor MC_LIGHTPURPLE = of(ChatFormatting.LIGHT_PURPLE);
    public static final TextColor MC_YELLOW = of(ChatFormatting.YELLOW);
    public static final TextColor MC_WHITE = of(ChatFormatting.WHITE);

    public static final TextColor CRIMSON = of("#8D230F");

    public static final TextColor RED = of(255, 0, 0);
    public static final TextColor ORANGE = of(255, 127, 0);
    public static final TextColor YELLOW = of(255, 255, 0);
    public static final TextColor LGREEN = of(127, 255, 0);
    public static final TextColor GREEN = of(0, 255, 0);
    public static final TextColor TURQOISE = of(0, 255, 127);
    public static final TextColor CYAN = of(0, 255, 255);
    public static final TextColor AUQUAMARINE = of(127,255,212);
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
    public static final TextColor SILVER_SAND = of(191,193,194);
    public static final TextColor SUN_GLOW = of(255,204,51);
    public static final TextColor CORN_FLOWER_BLUE = of(100,149,237);
    public static final TextColor APRICOT = of(251,206,177);
    public static final TextColor KEY_LIME = of(232,244,140);
    public static final TextColor BRIGHT_CERULEAN = of(29,172,214);

    public static int getRed(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int getGreen(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int getBlue(int rgb) {
        return rgb & 0xFF;
    }

    private static TextColor of(ChatFormatting formatColor) {
        return TextColor.fromLegacyFormat(formatColor);
    }

    private static TextColor of(String formatColor) {
        return TextColor.parseColor(formatColor).getOrThrow(false, (msg)-> {});
    }

    private static TextColor of(int red, int green, int blue) {
        var rgb = ((red & 0xFF) << 16) |
                  ((green & 0xFF) << 8)  |
                  ((blue & 0xFF));
        return TextColor.fromRgb(rgb);
    }
}