package org.orecruncher.dsurround.lib.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;

import java.awt.*;

@Environment(EnvType.CLIENT)
public final class ColorPalette {
    // Minecraft colors mapped to codes
    public static final Color MC_BLACK = new Color(Formatting.BLACK.getColorValue());
    public static final Color MC_DARKBLUE = new Color(Formatting.DARK_BLUE.getColorValue());
    public static final Color MC_DARKGREEN = new Color(Formatting.DARK_GREEN.getColorValue());
    public static final Color MC_DARKAQUA = new Color(Formatting.DARK_AQUA.getColorValue());
    public static final Color MC_DARKRED = new Color(Formatting.DARK_RED.getColorValue());
    public static final Color MC_DARKPURPLE = new Color(Formatting.DARK_PURPLE.getColorValue());
    public static final Color MC_GOLD = new Color(Formatting.GOLD.getColorValue());
    public static final Color MC_GRAY = new Color(Formatting.GRAY.getColorValue());
    public static final Color MC_DARKGRAY = new Color(Formatting.DARK_GRAY.getColorValue());
    public static final Color MC_BLUE = new Color(Formatting.BLUE.getColorValue());
    public static final Color MC_GREEN = new Color(Formatting.GREEN.getColorValue());
    public static final Color MC_AQUA = new Color(Formatting.AQUA.getColorValue());
    public static final Color MC_RED = new Color(Formatting.RED.getColorValue());
    public static final Color MC_LIGHTPURPLE = new Color(Formatting.LIGHT_PURPLE.getColorValue());
    public static final Color MC_YELLOW = new Color(Formatting.YELLOW.getColorValue());
    public static final Color MC_WHITE = new Color(Formatting.WHITE.getColorValue());

    public static final Color RED = new Color(255, 0, 0);
    public static final Color ORANGE = new Color(255, 127, 0);
    public static final Color YELLOW = new Color(255, 255, 0);
    public static final Color LGREEN = new Color(127, 255, 0);
    public static final Color GREEN = new Color(0, 255, 0);
    public static final Color TURQOISE = new Color(0, 255, 127);
    public static final Color CYAN = new Color(0, 255, 255);
    public static final Color AUQUAMARINE = new Color(0, 127, 255);
    public static final Color BLUE = new Color(0, 0, 255);
    public static final Color VIOLET = new Color(127, 0, 255);
    public static final Color MAGENTA = new Color(255, 0, 255);
    public static final Color RASPBERRY = new Color(255, 0, 127);
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color PURPLE = new Color(80, 0, 80);
    public static final Color INDIGO = new Color(75, 0, 130);
    public static final Color NAVY = new Color(0, 0, 128);
    public static final Color TAN = new Color(210, 180, 140);
    public static final Color GOLD = new Color(255, 215, 0);
    public static final Color GRAY = new Color(128, 128, 128);
    public static final Color LGRAY = new Color(192, 192, 192);
    public static final Color SLATEGRAY = new Color(112, 128, 144);
    public static final Color DARKSLATEGRAY = new Color(47, 79, 79);

    public static final Color AURORA_RED = new Color(1.0F, 0F, 0F);
    public static final Color AURORA_GREEN = new Color(0.5F, 1.0F, 0.0F);
    public static final Color AURORA_BLUE = new Color(0F, 0.8F, 1.0F);

    public static Color fromHTMLColorCode(String code) {
        return Color.decode(code);
    }

    public static String toHTMLColorCode(Color color) {
        StringBuilder builder = new StringBuilder();
        builder.append("#");
        if (color.getAlpha() != 255)
            builder.append(String.format("%02X", color.getAlpha()));
        builder.append(String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
        return builder.toString();
    }
}