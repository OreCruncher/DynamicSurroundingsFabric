package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.Localization;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;

import java.util.ArrayList;
import java.util.Collection;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private Style titleStyle = Style.EMPTY;
    private Style propertyGroupStyle = Style.EMPTY;
    private Style propertyStyle = Style.EMPTY;
    private Style tooltipStyle = Style.EMPTY;
    private boolean wrapToolTip = false;
    private int toolTipWidth = 300;

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

    public ConfigOptions setTooltipWidth(int width) {
        this.toolTipWidth = width;
        return this;
    }

    public ConfigOptions wrapToolTip(boolean flag) {
        this.wrapToolTip = flag;
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
        return this.transformTitle(this.titleStyle);
    }

    public Component transformTitle(Style style) {
        if (style.isEmpty()) {
            style = this.titleStyle;
        }
        var txt = Localization.load(this.translationRoot + ".title");
        return Component.literal(txt).withStyle(style);
    }

    public Component transformPropertyGroup(String langKey) {
        return this.transformPropertyGroup(langKey, this.propertyGroupStyle);
    }

    public Component transformPropertyGroup(String langKey, Style style) {
        if (style.isEmpty()) {
            style = this.propertyGroupStyle;
        }
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(style);
    }

    public Component transformProperty(String langKey) {
        return this.transformProperty(langKey, this.propertyStyle);
    }

    public Component transformProperty(String langKey, Style style) {
        if (style.isEmpty()) {
            style = this.propertyStyle;
        }
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(style);
    }

    public Collection<Component> transformTooltip(Component tooltip) {
        return this.transformTooltip(tooltip, this.tooltipStyle);
    }

    public Collection<Component> transformTooltip(Component tooltip, Style style) {
        if (this.wrapToolTip)
            return GuiHelpers.getTrimmedTextCollection(tooltip, toolTipWidth, style);
        var result = new ArrayList<Component>();
        result.add(tooltip);
        return result;
    }
}