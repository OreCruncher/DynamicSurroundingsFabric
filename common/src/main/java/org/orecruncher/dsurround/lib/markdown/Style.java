package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/**
 * The formatting of one run of text. Immutable, so two styles with the same values are equal and can be compared
 * with {@link #equals(Object)}.
 * <p>
 * The color and font are typed values, or null to inherit. The boolean flags are {@code null} when "off",
 * never {@code false}, so an unset flag is simply absent from the output and inherits from its parent.
 */
record Style(
        TextColor color,
        ResourceLocation font,
        Boolean bold,
        Boolean italic,
        Boolean underline,
        Boolean strikethrough,
        String clickEventUrl,
        String hoverEventText) {

    static final Style EMPTY = new Style(null, null, null, null, null, null, null, null);

    Style withColor(TextColor color) {
        return new Style(color, this.font, this.bold, this.italic, this.underline, this.strikethrough,
                this.clickEventUrl, this.hoverEventText);
    }

    Style withFont(ResourceLocation font) {
        return new Style(this.color, font, this.bold, this.italic, this.underline, this.strikethrough,
                this.clickEventUrl, this.hoverEventText);
    }

    Style withItalic(Boolean italic) {
        return new Style(this.color, this.font, this.bold, italic, this.underline, this.strikethrough,
                this.clickEventUrl, this.hoverEventText);
    }
}