package org.orecruncher.dsurround.lib.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.ArrayList;
import java.util.Collection;

public class GuiHelpers {

    private final static String ELLIPSES = "...";

    /**
     * Gets the text associated with the given language key that is formatted so that a line is <= the width
     * specified.
     *
     * @param key       Translation key for the associated text
     * @param width     Maximum width of a line
     * @param style     The style to apply to each of the resulting split lines
     * @return Collection of Components for the given key
     */
    public static Collection<Component> getTrimmedTextCollection(final String key, final int width, final Style style) {
        var text = Component.translatable(key);
        return getTrimmedTextCollection(text, width, style);
    }

    public static Collection<Component> getTrimmedTextCollection(Component text, int width, Style style) {
        var result = new ArrayList<Component>();
        var textHandler = GameUtils.getTextHandler();
        textHandler.splitLines(text, width, style).forEach(line -> result.add(Component.literal(line.getString()).withStyle(style)));
        return result;
    }

    /**
     * Gets the text associated with the given language key.  Text is truncated to the specified width and an
     * ellipses append if necessary.
     *
     * @param key        Translation key for the associated text
     * @param width      Maximum width of the text in GUI pixels
     * @param formatting Formatting to apply to the text
     * @return FormattedText fitting the criteria specified
     */
    public static FormattedText getTrimmedText(final String key, final int width, @Nullable final ChatFormatting... formatting) {
        var fr = GameUtils.getTextRenderer();
        var cm = GameUtils.getTextHandler();

        final Style style = prefixHelper(formatting);
        final FormattedText text = Component.translatable(key);
        if (fr.width(text) > width) {
            final int ellipsesWidth = fr.width(ELLIPSES);
            final int trueWidth = width - ellipsesWidth;
            final FormattedText str = cm.headByWidth(text, trueWidth, style);
            return Component.literal(str.getString() + ELLIPSES);
        }
        final FormattedText str = cm.headByWidth(text, width, style);
        return Component.literal(str.getString());
    }

    private static Style prefixHelper(@Nullable final ChatFormatting[] formatting) {
        final Style style;
        if (formatting != null && formatting.length > 0)
            style = Style.EMPTY.applyFormats(formatting);
        else
            style = Style.EMPTY;
        return style;
    }
}