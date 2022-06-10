package org.orecruncher.dsurround.lib.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Collection;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class GuiHelpers {

    private final static String ELLIPSES = "...";

    /**
     * Gets the text associated with the given language key that is formatted so that a line is <= the width
     * specified.
     *
     * @param key        Translation key for the associated text
     * @param width      Maximum width of a line
     * @param formatting Formatting to apply to each line
     * @return Collection of ITextComponents for the given key
     */
    public static Collection<OrderedText> getTrimmedTextCollection(final String key, final int width, @Nullable final Formatting... formatting) {
        final Style style = prefixHelper(formatting);
        return GameUtils.getTextHandler()
                .wrapLines(
                        Text.translatable(key),
                        width,
                        style)
                .stream().map(e -> Text.of(e.getString()).asOrderedText())
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
    public static StringVisitable getTrimmedText(final String key, final int width, @Nullable final Formatting... formatting) {
        final Style style = prefixHelper(formatting);
        final StringVisitable text = Text.translatable(key);
        final TextRenderer fr = GameUtils.getTextRenderer();
        final TextHandler cm = fr.getTextHandler();
        if (fr.getWidth(text) > width) {
            final int ellipsesWidth = fr.getWidth(ELLIPSES);
            final int trueWidth = width - ellipsesWidth;
            final StringVisitable str = cm.trimToWidth(text, trueWidth, style);
            return Text.of(str.getString() + ELLIPSES);
        }
        final StringVisitable str = cm.trimToWidth(text, width, style);
        return Text.of(str.getString());
    }

    private static Style prefixHelper(@Nullable final Formatting[] formatting) {
        final Style style;
        if (formatting != null && formatting.length > 0)
            style = Style.EMPTY.withFormatting(formatting);
        else
            style = Style.EMPTY;
        return style;
    }
}