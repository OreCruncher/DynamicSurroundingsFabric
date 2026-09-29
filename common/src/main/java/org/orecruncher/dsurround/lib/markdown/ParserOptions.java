package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * Configuration options for customizing parser styling behaviors.
 */
public final class ParserOptions {

    private static final String DEFAULT_BULLET = "• ";
    private static final String DEFAULT_BLOCK_QUOTE = "│ ";

    private String defaultHeadingColor;
    private String defaultLinkColor;
    private String defaultBulletColor;
    private String defaultTextColor;
    private String defaultQuoteColor;
    private String font;

    private String unorderedBullet;
    private String quotePrefix;

    private ParserOptions() {
        this.defaultHeadingColor = ColorPalette.MC_GOLD.formatValue();
        this.defaultLinkColor = ColorPalette.MC_BLUE.formatValue();
        this.defaultBulletColor = ColorPalette.MC_GRAY.formatValue();
        this.defaultTextColor = null;
        this.defaultQuoteColor = ColorPalette.MC_GRAY.formatValue();;
        this.font = null;

        this.unorderedBullet = DEFAULT_BULLET;
        this.quotePrefix = DEFAULT_BLOCK_QUOTE;
    }

    public String defaultHeadingColor() {
        return this.defaultHeadingColor;
    }

    public String defaultLinkColor() {
        return this.defaultLinkColor;
    }

    public String defaultBulletColor() {
        return this.defaultBulletColor;
    }

    public String defaultTextColor() {
        return this.defaultTextColor;
    }

    public String defaultQuoteColor() {
        return this.defaultQuoteColor;
    }

    public String font() {
        return this.font;
    }

    public String unorderedBullet() {
        return this.unorderedBullet;
    }

    public String quotePrefix() {
        return this.quotePrefix;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static ParserOptions DEFAULT = new Builder().build();
    public static ParserOptions UNIFORM = new Builder().font(ResourceLocation.withDefaultNamespace("uniform")).build();

    public static class Builder {

        private final ParserOptions options = new ParserOptions();

        public Builder() { }

        public Builder defaultHeadingColor(TextColor color) {
            return this.defaultHeadingColor(color.formatValue());
        }

        public Builder defaultHeadingColor(String defaultHeadingColor) {
            this.options.defaultHeadingColor = StringUtils.isNoneBlank(defaultHeadingColor) ? null : defaultHeadingColor;
            return this;
        }

        public Builder defaultLinkColor(TextColor color) {
            return this.defaultLinkColor(color.formatValue());
        }

        public Builder defaultLinkColor(String color) {
            this.options.defaultLinkColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder defaultBulletColor(TextColor color) {
            return this.defaultBulletColor(color.formatValue());
        }

        public Builder defaultBulletColor(String color) {
            this.options.defaultBulletColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder defaultTextColor(TextColor color) {
            return this.defaultTextColor(color.formatValue());
        }

        public Builder defaultTextColor(String color) {
            this.options.defaultTextColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder defaultQuoteColor(TextColor color) {
            return this.defaultQuoteColor(color.formatValue());
        }

        public Builder defaultQuoteColor(String color) {
            this.options.defaultQuoteColor = StringUtils.isNoneBlank(color) ? null : color;
            return this;
        }

        public Builder font(ResourceLocation font) {
            this.options.font = font.toString();
            return this;
        }

        public Builder unorderedBullet(String bullet) {
            this.options.unorderedBullet = StringUtils.isNoneBlank(bullet) ? DEFAULT_BULLET : bullet;
            return this;
        }

        public Builder quotePrefix(String prefix) {
            this.options.quotePrefix = StringUtils.isNoneBlank(prefix) ? DEFAULT_BLOCK_QUOTE : prefix;
            return this;
        }

        public ParserOptions build() {
            return this.options;
        }
    }
}