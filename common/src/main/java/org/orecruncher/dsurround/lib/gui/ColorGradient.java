package org.orecruncher.dsurround.lib.gui;

import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

public class ColorGradient {

    private final int sRed;
    private final int sGreen;
    private final int sBlue;
    private final int eRed;
    private final int eGreen;
    private final int eBlue;
    private final float scale;

    public ColorGradient(TextColor start, TextColor end, float scale) {
        this.scale = scale;

        var color = start.getValue();
        this.sRed = ColorPalette.getRed(color);
        this.sGreen = ColorPalette.getGreen(color);
        this.sBlue = ColorPalette.getBlue(color);

        color = end.getValue();
        this.eRed = ColorPalette.getRed(color);
        this.eGreen = ColorPalette.getGreen(color);
        this.eBlue = ColorPalette.getBlue(color);
    }

    public int getRGBColor(float delta) {
        var ratio = delta / this.scale;
        var red = (int) Mth.lerp(ratio, this.sRed, this.eRed);
        var green = (int)Mth.lerp(ratio, this.sGreen, this.eGreen);
        var blue = (int)Mth.lerp(ratio, this.sBlue, this.eBlue);

        return ColorPalette.toRGB(red, green, blue);
    }

    public TextColor getTextColor(float delta) {
        return TextColor.fromRgb(this.getRGBColor(delta));
    }
}
