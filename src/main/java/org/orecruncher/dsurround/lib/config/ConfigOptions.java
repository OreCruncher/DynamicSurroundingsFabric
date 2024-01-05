package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.Localization;

import java.util.*;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private Style propertyGroupStyle = Style.EMPTY;
    private Style propertyStyle = Style.EMPTY;
    private Style tooltipStyle = Style.EMPTY;
    private boolean stripTitle = true;

    public ConfigOptions() {

    }

    private static String strip(String txt) {
        var result = ChatFormatting.stripFormatting(txt);
        return result != null ? result : txt;
    }

    public ConfigOptions setPropertyGroupStyle(Style style) {
        this.propertyGroupStyle = style;
        return this;
    }

    public ConfigOptions setPropertyStyle(Style style) {
        this.propertyStyle = style;
        return this;
    }

    public ConfigOptions setTooltipStyle(Style style) {
        this.tooltipStyle = style;
        return this;
    }

    public Style getTooltipStyle() {
        return this.tooltipStyle;
    }

    public ConfigOptions setStripTitle(boolean v) {
        this.stripTitle = v;
        return this;
    }

    public ConfigOptions setTranslationRoot(String root) {
        this.translationRoot = root;
        return this;
    }

    public Component transformTitle() {
        var txt = Localization.load(this.translationRoot + ".title");
        if (this.stripTitle)
            txt = strip(txt);
        return Component.literal(txt);
    }

    public Component transformPropertyGroup(String langKey) {
        var txt = strip(Localization.load(langKey));
        return Component.literal(txt).withStyle(this.propertyGroupStyle);
    }

    public Component transformProperty(String langKey) {
        var txt = strip(Localization.load(langKey));
        return Component.literal(txt).withStyle(this.propertyStyle);
    }

    public Component[] transformTooltip(Collection<Component> tooltip) {
        return tooltip.toArray(new Component[0]);
    }
}
