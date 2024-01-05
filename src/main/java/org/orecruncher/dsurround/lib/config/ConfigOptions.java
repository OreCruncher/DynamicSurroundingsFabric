package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.Localization;

import java.util.*;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private Style titleStyle = Style.EMPTY;
    private Style propertyGroupStyle = Style.EMPTY;
    private Style propertyStyle = Style.EMPTY;
    private Style tooltipStyle = Style.EMPTY;

    public ConfigOptions() {

    }

    public ConfigOptions setTitleStyle(Style style) {
        this.titleStyle = style;
        return this;
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

    public ConfigOptions setTranslationRoot(String root) {
        this.translationRoot = root;
        return this;
    }

    public Component transformTitle() {
        var txt = Localization.load(this.translationRoot + ".title");
        return Component.literal(txt).withStyle(this.titleStyle);
    }

    public Component transformPropertyGroup(String langKey) {
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(this.propertyGroupStyle);
    }

    public Component transformProperty(String langKey) {
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(this.propertyStyle);
    }

    public Component[] transformTooltip(Collection<Component> tooltip) {
        return tooltip.toArray(new Component[0]);
    }
}
