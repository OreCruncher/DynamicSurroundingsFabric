package org.orecruncher.dsurround.lib.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Collection;
import java.util.stream.Collectors;

public class GuiHelpers {

    private final static String ELLIPSES = "...";

    /**
     * Gets the text associated with the given language key that is formatted so that a line is <= the width
     * specified.
     *
     * @param key       Translation key for the associated text
     * @param width     Maximum width of a line
     * @param style     The style to apply to each of the resulting split lines
     * @return Collection of ITextComponents for the given key
     */
    public static Collection<FormattedCharSequence> getTrimmedTextCollection(final String key, final int width, @Nullable final Style style) {
        var textHandler = GameUtils.getTextHandler();
        return textHandler
                .splitLines(
                        Component.translatable(key),
                        width,
                        style)
                .stream()
                .map(e -> Component.literal(e.getString()).withStyle(style).getVisualOrderText())
                .collect(Collectors.toList());
    }

    /**
     * Gets the text associated with the given language key.  Text is truncated to the specified width and an
     * ellipses append if necessary.
     *
     * @param key        Translation key for the associated text
     * @param width      Maximum width of the text in GUI pixels
     * @param formatting Formatting to apply to the text
     * @return ITextComponent fitting the criteria specified
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