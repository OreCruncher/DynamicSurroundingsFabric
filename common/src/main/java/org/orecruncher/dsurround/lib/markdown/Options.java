package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * Configuration options for customizing parser styling behaviors. Instances are immutable; use
 * {@link #builder()} to create customized ones.
 */
public final class Options {

    public static final Options DEFAULT = builder().build();
    public static final Options UNIFORM = builder().font(BuiltinFonts.UNIFORM).build();
    private static final String DEFAULT_BULLET = "• ";
    private static final String DEFAULT_BLOCK_QUOTE = "│ ";
    private static final String DEFAULT_HOVER_TEMPLATE = "%s";
    private static final String DEFAULT_HOVER_TEXT_LANG_KEY = "dsurround.text.markdown.hovertext";
    private final TextColor headingColor;
    private final TextColor linkColor;
    private final TextColor bulletColor;
    private final TextColor textColor;
    private final TextColor quoteColor;
    private final Identifier font;
    private final String bulletStyle;
    private final String quoteStyle;
    private final boolean quoteItalic;
    private final boolean colorOverridesLink;
    private final String linkHoverTemplate;
    private final String linkHoverTranslationKey;

    private Options(Builder b) {
        this.headingColor = b.headingColor;
        this.linkColor = b.linkColor;
        this.bulletColor = b.bulletColor;
        this.textColor = b.textColor;
        this.quoteColor = b.quoteColor;
        this.font = b.font;
        this.bulletStyle = b.bulletStyle;
        this.quoteStyle = b.quoteStyle;
        this.quoteItalic = b.quoteItalic;
        this.colorOverridesLink = b.colorOverridesLink;
        this.linkHoverTemplate = b.linkHoverTemplate;
        this.linkHoverTranslationKey = b.linkHoverTranslationKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Color for headings, or null to inherit the text color.
     */
    public TextColor headingColor() {
        return this.headingColor;
    }

    /**
     * Color for link text, or null to inherit.
     */
    public TextColor linkColor() {
        return this.linkColor;
    }

    /**
     * Color for bullet glyphs, or null to inherit.
     */
    public TextColor bulletColor() {
        return this.bulletColor;
    }

    /**
     * Base text color, or null for the renderer default.
     */
    public TextColor textColor() {
        return this.textColor;
    }

    /**
     * Color for block quotes, or null to inherit.
     */
    public TextColor quoteColor() {
        return this.quoteColor;
    }

    /**
     * The font to use, or null for the default font.
     */
    public Identifier font() {
        return this.font;
    }

    /**
     * Text inserted in front of each list item. May be empty, never null.
     */
    public String bulletStyle() {
        return this.bulletStyle;
    }

    /**
     * Text inserted in front of each quoted line. May be empty, never null.
     */
    public String quoteStyle() {
        return this.quoteStyle;
    }

    /**
     * Whether block quote text is italic by default. Headings inside a quote are never italicized.
     */
    public boolean quoteItalic() {
        return this.quoteItalic;
    }

    /**
     * Whether a {@code <color>} tag around or inside a link sets the link's color. Off by default, when links are
     * always in the link color.
     */
    public boolean colorOverridesLink() {
        return this.colorOverridesLink;
    }

    /**
     * Template for link hover text when no translation key is used. "%s" is replaced with the URL.
     */
    public String linkHoverTemplate() {
        return this.linkHoverTemplate;
    }

    /**
     * Translation key for link hover text. Empty string means "don't use a translation key". Never null.
     */
    public String linkHoverTranslationKey() {
        return this.linkHoverTranslationKey;
    }

    public static final class Builder {

        // Colors: null means "no explicit color" (inherit).
        private TextColor headingColor = ColorPalette.MC_GOLD;
        private TextColor linkColor = ColorPalette.MC_BLUE;
        private TextColor bulletColor = ColorPalette.MC_GRAY;
        private TextColor textColor = null;
        private TextColor quoteColor = ColorPalette.MC_GRAY;
        private Identifier font = null;

        private String bulletStyle = DEFAULT_BULLET;
        private String quoteStyle = DEFAULT_BLOCK_QUOTE;
        private boolean quoteItalic = true;
        private boolean colorOverridesLink = false;
        private String linkHoverTemplate = DEFAULT_HOVER_TEMPLATE;
        private String linkHoverTranslationKey = DEFAULT_HOVER_TEXT_LANG_KEY;

        private Builder() {
        }

        /**
         * Null clears the color, so the text inherits.
         */
        public Builder headingColor(TextColor color) {
            this.headingColor = color;
            return this;
        }

        /**
         * Color name or {@code #RRGGBB}. Null, blank or an unrecognized color clears the color.
         */
        public Builder headingColor(String color) {
            this.headingColor = Colors.parse(color);
            return this;
        }

        /**
         * Null clears the color, so the text inherits.
         */
        public Builder linkColor(TextColor color) {
            this.linkColor = color;
            return this;
        }

        /**
         * Color name or {@code #RRGGBB}. Null, blank or an unrecognized color clears the color.
         */
        public Builder linkColor(String color) {
            this.linkColor = Colors.parse(color);
            return this;
        }

        /**
         * Null clears the color, so the text inherits.
         */
        public Builder bulletColor(TextColor color) {
            this.bulletColor = color;
            return this;
        }

        /**
         * Color name or {@code #RRGGBB}. Null, blank or an unrecognized color clears the color.
         */
        public Builder bulletColor(String color) {
            this.bulletColor = Colors.parse(color);
            return this;
        }

        /**
         * Null clears the color, so the text inherits.
         */
        public Builder textColor(TextColor color) {
            this.textColor = color;
            return this;
        }

        /**
         * Color name or {@code #RRGGBB}. Null, blank or an unrecognized color clears the color.
         */
        public Builder textColor(String color) {
            this.textColor = Colors.parse(color);
            return this;
        }

        /**
         * Null clears the color, so the text inherits.
         */
        public Builder quoteColor(TextColor color) {
            this.quoteColor = color;
            return this;
        }

        /**
         * Color name or {@code #RRGGBB}. Null, blank or an unrecognized color clears the color.
         */
        public Builder quoteColor(String color) {
            this.quoteColor = Colors.parse(color);
            return this;
        }

        public Builder font(Identifier font) {
            this.font = font;
            return this;
        }

        /**
         * Null restores the default bullet. An empty string is allowed and means "no bullet glyph".
         */
        public Builder bulletStyle(String bullet) {
            this.bulletStyle = bullet == null ? DEFAULT_BULLET : bullet;
            return this;
        }

        /**
         * Null restores the default prefix. An empty string is allowed and means "no prefix".
         */
        public Builder quoteStyle(String prefix) {
            this.quoteStyle = prefix == null ? DEFAULT_BLOCK_QUOTE : prefix;
            return this;
        }

        /**
         * Whether block quote text should be italic. Defaults to true.
         */
        public Builder quoteItalic(boolean italic) {
            this.quoteItalic = italic;
            return this;
        }

        /**
         * Whether a {@code <color>} tag sets the color of a link inside it, rather than the link color always
         * applying. Defaults to false.
         */
        public Builder colorOverridesLink(boolean overrides) {
            this.colorOverridesLink = overrides;
            return this;
        }

        /**
         * Blank restores the default template ("%s").
         */
        public Builder linkHoverTemplate(String template) {
            this.linkHoverTemplate = (template == null || template.isBlank()) ? DEFAULT_HOVER_TEMPLATE : template;
            return this;
        }

        /**
         * Null restores the default key. An empty string disables the translation and uses the hover template.
         */
        public Builder linkHoverTranslationKey(String translationKey) {
            this.linkHoverTranslationKey = translationKey == null ? DEFAULT_HOVER_TEXT_LANG_KEY : translationKey;
            return this;
        }

        public Options build() {
            return new Options(this);
        }

    }

    public static final class BuiltinFonts {
        public static final Identifier DEFAULT = Identifier.withDefaultNamespace("default");
        public static final Identifier UNIFORM = Identifier.withDefaultNamespace("uniform");
        public static final Identifier GALACTIC = Identifier.withDefaultNamespace("galactic");
        public static final Identifier ILLAGERALT = Identifier.withDefaultNamespace("illageralt");

        private BuiltinFonts() {
        }
    }
}