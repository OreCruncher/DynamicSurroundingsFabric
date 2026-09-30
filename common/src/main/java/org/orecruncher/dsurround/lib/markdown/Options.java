package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * Configuration options for customizing parser styling behaviors.
 */
public final class Options {

    private static final String DEFAULT_BULLET = "• ";
    private static final String DEFAULT_BLOCK_QUOTE = "│ ";
    private static final String DEFAULT_HOVER_TEMPLATE = "%s";
    private static final String DEFAULT_HOVER_TEXT_LANG_KEY = "dsurround.text.markdown.hovertext";
    public static Options DEFAULT = new Builder().build();
    public static Options UNIFORM = new Builder().font(BuiltinFonts.UNIFORM).build();
    private String defaultHeadingColor;
    private String defaultLinkColor;
    private String defaultBulletColor;
    private String defaultTextColor;
    private String defaultQuoteColor;
    private String font;
    private String bulletStyle;
    private String quoteStyle;
    private String linkHoverTemplate;
    private String linkHoverTranslationKey;

    private Options() {
        this.defaultHeadingColor = ColorPalette.MC_GOLD.formatValue();
        this.defaultLinkColor = ColorPalette.MC_BLUE.formatValue();
        this.defaultBulletColor = ColorPalette.MC_GRAY.formatValue();
        this.defaultTextColor = null;
        this.defaultQuoteColor = ColorPalette.MC_GRAY.formatValue();
        this.font = null;

        this.bulletStyle = DEFAULT_BULLET;
        this.quoteStyle = DEFAULT_BLOCK_QUOTE;
        this.linkHoverTemplate = DEFAULT_HOVER_TEMPLATE;
        this.linkHoverTranslationKey = DEFAULT_HOVER_TEXT_LANG_KEY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String headingColor() {
        return this.defaultHeadingColor;
    }

    public String linkColor() {
        return this.defaultLinkColor;
    }

    public String bulletColor() {
        return this.defaultBulletColor;
    }

    public String textColor() {
        return this.defaultTextColor;
    }

    public String quoteColor() {
        return this.defaultQuoteColor;
    }

    public String font() {
        return this.font;
    }

    public String bulletStyle() {
        return this.bulletStyle;
    }

    public String quoteStyle() {
        return this.quoteStyle;
    }

    public String linkHoverTemplate() {
        return this.linkHoverTemplate;
    }

    public String linkHoverTranslationKey() {
        return this.linkHoverTranslationKey;
    }

    public static class Builder {

        private final Options options = new Options();

        public Builder() {
        }

        public Builder headingColor(TextColor color) {
            return this.headingColor(color.formatValue());
        }

        public Builder headingColor(String defaultHeadingColor) {
            this.options.defaultHeadingColor = StringUtils.isNoneBlank(defaultHeadingColor) ? null : defaultHeadingColor;
            return this;
        }

        public Builder linkColor(TextColor color) {
            return this.linkColor(color.formatValue());
        }

        public Builder linkColor(String color) {
            this.options.defaultLinkColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder bulletColor(TextColor color) {
            return this.bulletColor(color.formatValue());
        }

        public Builder bulletColor(String color) {
            this.options.defaultBulletColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder textColor(TextColor color) {
            return this.textColor(color.formatValue());
        }

        public Builder textColor(String color) {
            this.options.defaultTextColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder quoteColor(TextColor color) {
            return this.quoteColor(color.formatValue());
        }

        public Builder quoteColor(String color) {
            this.options.defaultQuoteColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder font(ResourceLocation font) {
            this.options.font = font.toString();
            return this;
        }

        public Builder bulletStyle(String bullet) {
            this.options.bulletStyle = StringUtils.isNoneBlank(bullet) ? DEFAULT_BULLET : bullet;
            return this;
        }

        public Builder quoteStyle(String prefix) {
            this.options.quoteStyle = StringUtils.isNoneBlank(prefix) ? DEFAULT_BLOCK_QUOTE : prefix;
            return this;
        }

        public Builder linkHoverTemplate(String template) {
            this.options.linkHoverTemplate = StringUtils.isNoneBlank(template) ? DEFAULT_HOVER_TEMPLATE : template;
            return this;
        }

        public Builder linkHoverTranslationKey(String translationKey) {
            this.options.linkHoverTranslationKey = StringUtils.isNoneBlank(translationKey) ? DEFAULT_HOVER_TEXT_LANG_KEY : translationKey;
            return this;
        }

        public Options build() {
            return this.options;
        }
    }

    public static class BuiltinFonts {
        public static final ResourceLocation DEFAULT = ResourceLocation.withDefaultNamespace("default");
        public static final ResourceLocation UNIFORM = ResourceLocation.withDefaultNamespace("uniform");
        public static final ResourceLocation GALACTIC = ResourceLocation.withDefaultNamespace("galactic");
        public static final ResourceLocation ILLAGERALT = ResourceLocation.withDefaultNamespace("illageralt");
    }
}