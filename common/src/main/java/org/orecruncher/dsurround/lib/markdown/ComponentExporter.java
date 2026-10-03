package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Converts a list of styled {@link Segment}s directly into a Minecraft {@link Component}, replacing the old
 * JSON export + codec round trip.
 * <p>
 * The markdown package has its own {@code Style} type, so Minecraft's is always written out in full here.
 * Everything in this class is static, so there is no instance state to qualify with {@code this}.
 */
final class ComponentExporter {

    private ComponentExporter() {
    }

    /**
     * Returns an empty root component with one child per segment, in order. Every segment carries its complete style,
     * so nothing relies on the root for formatting.
     */
    static MutableComponent export(List<Segment> segments, Options options) {
        MutableComponent root = Component.empty();
        for (Segment segment : segments) {
            root.append(Component.literal(segment.text()).withStyle(toStyle(segment.style(), options)));
        }
        return root;
    }

    private static net.minecraft.network.chat.Style toStyle(Style s, Options options) {
        var style = colorAndFont(s);

        // Flags are null when "off". Leaving them unset (rather than false) lets the value inherit from the parent.
        if (s.bold() != null) {
            style = style.withBold(s.bold());
        }
        if (s.italic() != null) {
            style = style.withItalic(s.italic());
        }
        if (s.underline() != null) {
            style = style.withUnderlined(s.underline());
        }
        if (s.strikethrough() != null) {
            style = style.withStrikethrough(s.strikethrough());
        }
        if (s.clickEventUrl() != null) {
            style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, s.clickEventUrl()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverContents(s, options)));
        }
        return style;
    }

    /**
     * Hover text for a link: its title if it has one. Otherwise, with a translation key the URL is passed as the
     * translation argument; without one the URL is substituted into {@link Options#linkHoverTemplate()}. The
     * replacement is literal, so a stray {@code %} in the template is harmless.
     */
    private static Component hoverContents(Style s, Options options) {
        if (s.linkTitle() != null) {
            return Component.literal(s.linkTitle()).withStyle(fontOnly(s));
        }
        String url = s.clickEventUrl();
        String key = options.linkHoverTranslationKey();
        if (!key.isEmpty()) {
            MutableComponent urlComponent = Component.literal(url).withStyle(colorAndFont(s));
            return Component.translatable(key, urlComponent).withStyle(fontOnly(s));
        }
        String text = options.linkHoverTemplate().replace("%s", url);
        return Component.literal(text).withStyle(colorAndFont(s));
    }

    private static net.minecraft.network.chat.Style colorAndFont(Style s) {
        var style = fontOnly(s);
        if (s.color() != null) {
            style = style.withColor(s.color());
        }
        return style;
    }

    private static net.minecraft.network.chat.Style fontOnly(Style s) {
        var style = net.minecraft.network.chat.Style.EMPTY;
        if (s.font() != null) {
            style = style.withFont(s.font());
        }
        return style;
    }
}