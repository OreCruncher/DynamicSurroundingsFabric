package org.orecruncher.dsurround.lib.config;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.Localization;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Presentation options for a generated config screen: translation root, text styles and tooltip wrapping.
 */
public class ConfigOptions {

    private static final int TOOLTIP_WIDTH = 300;

    private String translationRoot = "";
    private Style titleStyle = Style.EMPTY;
    private Style propertyGroupStyle = Style.EMPTY;
    private Style propertyStyle = Style.EMPTY;
    private Style tooltipStyle = Style.EMPTY;
    private boolean wrapToolTip = false;

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
        var txt = Localization.load(this.translationRoot + ".title");
        return Component.literal(txt).withStyle(this.titleStyle);
    }

    /**
     * The group's name, in {@code style}, or the group style if it is empty.
     */
    public Component transformPropertyGroup(String langKey, Style style) {
        if (style.isEmpty()) {
            style = this.propertyGroupStyle;
        }
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(style);
    }

    /**
     * The property's name, in {@code style}, or the property style if it is empty.
     */
    public Component transformProperty(String langKey, Style style) {
        if (style.isEmpty()) {
            style = this.propertyStyle;
        }
        var txt = Localization.load(langKey);
        return Component.literal(txt).withStyle(style);
    }

    /**
     * The tooltip as lines, wrapped if wrapping is enabled. The collection can be added to.
     */
    public Collection<Component> transformTooltip(Component tooltip) {
        if (this.wrapToolTip)
            return GuiHelpers.getTrimmedTextCollection(tooltip, TOOLTIP_WIDTH, this.tooltipStyle);
        var result = new ArrayList<Component>();
        result.add(tooltip);
        return result;
    }
}