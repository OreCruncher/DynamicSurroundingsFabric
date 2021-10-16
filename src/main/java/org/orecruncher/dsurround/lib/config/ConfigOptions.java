package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Localization;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private Style titleStyle = Style.EMPTY;
    private Style subtitleStyle = Style.EMPTY;
    private Style propertyGroupStyle = Style.EMPTY;
    private Style propertyValueStyle = Style.EMPTY;
    private Style tooltipStyle = Style.EMPTY;
    private int toolTipWidth = 200;
    private boolean stripTitle = true;
    private boolean stripPropertyGroups = true;
    private boolean stripPropertyValues = true;

    public ConfigOptions() {

    }

    public ConfigOptions setTranslationRoot(String root) {
        this.translationRoot = root;
        return this;
    }

    public ConfigOptions setTitleStyle(Style style) {
        this.titleStyle = style;
        return this;
    }

    public ConfigOptions setSubtitleStyle(Style style) {
        this.subtitleStyle = style;
        return this;
    }

    public ConfigOptions setPropertyGroupStyle(Style style) {
        this.propertyGroupStyle = style;
        return this;
    }

    public ConfigOptions setPropertyValueStyle(Style style) {
        this.propertyValueStyle = style;
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

    public ConfigOptions setStripTitle(boolean v) {
        this.stripTitle = v;
        return this;
    }

    public ConfigOptions setStripPropertyGroups(boolean v) {
        this.stripPropertyGroups = v;
        return this;
    }

    public ConfigOptions setStripPropertyValues(boolean v) {
        this.stripPropertyValues = v;
        return this;
    }

    public String getTranslationRoot() {
        return this.translationRoot;
    }

    public int getTooltipWidth() {
        return this.toolTipWidth;
    }

    public Text transformTitle() {
        var txt = Localization.load(this.translationRoot + ".title");
        if (this.stripTitle)
            txt = strip(txt);
        return new TranslatableText(txt).fillStyle(this.titleStyle);
    }

    public Text transformSubtitle() {
        var txt = Localization.load(this.translationRoot + ".subtitle");
        if (this.stripTitle)
            txt = strip(txt);
        return new TranslatableText(txt).fillStyle(this.subtitleStyle);
    }

    public Text transformPropertyGroup(String langKey) {
        var txt = Localization.load(langKey);
        if (this.stripPropertyGroups)
            txt = strip(txt);
        return new TranslatableText(txt).fillStyle(this.propertyGroupStyle);
    }

    public Text transformProperty(String langKey) {
        var txt = Localization.load(langKey);
        if (this.stripPropertyValues)
            txt = strip(txt);
        return new TranslatableText(txt).fillStyle(this.propertyValueStyle);
    }

    public Text[] transformTooltip(List<Text> tooltip) {
        var result = new ArrayList<Text>(tooltip.size());
        for (var e : tooltip) {
            var wrapped =  GameUtils.getTextHandler()
                    .wrapLines(
                            e,
                            this.toolTipWidth,
                            this.tooltipStyle)
                    .stream()
                    .map(l -> new LiteralText(l.getString()))
                    .collect(Collectors.toList());
            result.addAll(wrapped);
        }

        return result.toArray(new Text[0]);
    }

    private static String strip(String txt) {
        var result = Formatting.strip(txt);
        return result != null ? result : txt;
    }
}
