package org.orecruncher.dsurround.lib.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

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
    public static final TextColor TURQUOISE = of(0, 255, 127);
    public static final TextColor CYAN = of(0, 255, 255);
    public static final TextColor AQUAMARINE = of(127,255,212);
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
    public static final TextColor BURNT_UMBER = of(110, 38, 14);
    public static final TextColor GOLDENROD = of(218, 165, 32);
    public static final TextColor WHEAT = of(245, 222, 179);
    public static final TextColor PUMPKIN_ORANGE = of(255, 117, 24);
    public static final TextColor DESERT = of(250, 213, 165);
    public static final TextColor CORNSILK = of(255, 248, 220);
    public static final TextColor BRASS = of(225, 193, 110);
    public static final TextColor ECRU = of(194, 178, 128);
    public static final TextColor SEASHELL = of(255, 245, 238);
    public static final TextColor ORCHID = of(218,112,214);
    public static final TextColor PALE_BROWN = of(152,118,84);
    public static final TextColor DARK_VIOLET = of(148,0,211);
    public static final TextColor ANTIQUE_WHITE = of(250,235,215);
    public static final TextColor PEARLY_PURPLE = of(183,104,162);
    public static final TextColor FRESH_AIR = of(166,231,255);
    public static final TextColor HOT_PINK = of("#ff69b4");

    public static final TextColor LEMON = of(254, 251, 1);
    public static final TextColor ELECTRIC_GREEN = of(0, 237, 1);

    public static int getRed(int rgb) {
        return FastColor.ARGB32.red(rgb);
    }

    public static int getGreen(int rgb) {
        return FastColor.ARGB32.green(rgb);
    }

    public static int getBlue(int rgb) {
        return FastColor.ARGB32.blue(rgb);
    }

    private static TextColor of(ChatFormatting formatColor) {
        return TextColor.fromLegacyFormat(formatColor);
    }

    private static TextColor of(String formatColor) {
        return TextColor.parseColor(formatColor).getOrThrow();
    }

    private static TextColor of(int rgb) {
        return TextColor.fromRgb(rgb);
    }

    private static TextColor of(int red, int green, int blue) {
        return of(toRGB(red, green, blue));
    }

    static int toRGB(int red, int green, int blue) {
        return FastColor.ARGB32.color(red, green, blue);
    }

    public static TextColor lerp(float scale, TextColor start, TextColor end) {
        var startRed = getRed(start.getValue());
        var startGreen = getGreen(start.getValue());
        var startBlue = getBlue(start.getValue());
        var endRed = getRed(end.getValue());
        var endGreen = getGreen(end.getValue());
        var endBlue = getBlue(end.getValue());

        var red = Mth.lerpInt(scale, startRed, endRed);
        var green = Mth.lerpInt(scale, startGreen, endGreen);
        var blue = Mth.lerpInt(scale, startBlue, endBlue);

        return of(red, green, blue);
    }
}