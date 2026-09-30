package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one place that turns color text into a {@link TextColor}, used both for {@code <color:...>} tags and for the
 * string overloads on {@link Options.Builder}.
 * <p>
 * Accepted: the 16 Minecraft color names (any case) and {@code #RRGGBB} with exactly six hex digits (any case).
 * That is stricter than {@link TextColor#parseColor}, which also takes things like {@code +ff} and {@code #fff}.
 */
final class Colors {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private Colors() {
    }

    /**
     * Returns the color, or null if the text is null, blank, or not a color Minecraft knows.
     */
    static TextColor parse(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("#")) {
            return HEX.matcher(text).matches() ? TextColor.parseColor(text).result().orElse(null) : null;
        }
        // Minecraft only knows lowercase names
        return TextColor.parseColor(text.toLowerCase(Locale.ROOT)).result().orElse(null);
    }
}
