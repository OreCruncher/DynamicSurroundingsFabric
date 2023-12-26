package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private String titleStyle = Strings.EMPTY;
    private String subtitleStyle = Strings.EMPTY;
    private String propertyGroupStyle = Strings.EMPTY;
    private String propertyValueStyle = Strings.EMPTY;
    private String tooltipStyle = Strings.EMPTY;
    private int toolTipWidth = 300;
    private boolean stripTitle = true;
    private boolean stripPropertyGroups = true;
    private boolean stripPropertyValues = true;

    public ConfigOptions() {

    }

    private static String strip(String txt) {
        var result = Formatting.strip(txt);
        return result != null ? result : txt;
    }

    public ConfigOptions setTitleStyle(Formatting... style) {
        this.titleStyle = combine(style);
        return this;
    }

    public ConfigOptions setSubtitleStyle(Formatting... style) {
        this.subtitleStyle = combine(style);
        return this;
    }

    public ConfigOptions setPropertyGroupStyle(Formatting... style) {
        this.propertyGroupStyle = combine(style);
        return this;
    }

    public ConfigOptions setPropertyValueStyle(Formatting... style) {
        this.propertyValueStyle  = combine(style);
        return this;
    }

    public ConfigOptions setTooltipStyle(Formatting... style) {
        this.tooltipStyle  = combine(style);
        return this;
    }

    private static String combine(Formatting... style) {
        return Arrays.stream(style).map(Objects::toString).collect(Collectors.joining(""));
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

    public ConfigOptions setTranslationRoot(String root) {
        this.translationRoot = root;
        return this;
    }

    public int getTooltipWidth() {
        return this.toolTipWidth;
    }

    public ConfigOptions setTooltipWidth(int width) {
        this.toolTipWidth = width;
        return this;
    }

    public Text transformTitle() {
        var txt = Localization.load(this.translationRoot + ".title");
        if (this.stripTitle)
            txt = strip(txt);
        return Text.of(this.titleStyle + txt);
    }

    public Text transformSubtitle() {
        var txt = Localization.load(this.translationRoot + ".subtitle");
        if (this.stripTitle)
            txt = strip(txt);
        return Text.of(this.subtitleStyle + txt);
    }

    public Text transformPropertyGroup(String langKey) {
        var txt = Localization.load(langKey);
        if (this.stripPropertyGroups)
            txt = strip(txt);
        return Text.of(this.propertyGroupStyle + txt);
    }

    public Text transformProperty(String langKey) {
        var txt = Localization.load(langKey);
        if (this.stripPropertyValues)
            txt = strip(txt);
        return Text.of(this.propertyValueStyle + txt);
    }

    public Text[] transformTooltip(List<Text> tooltip) {
        var textHandler = GameUtils.getTextHandler().orElseThrow();
        var result = new ArrayList<Text>(tooltip.size());
        for (var e : tooltip) {
            var wrapped = textHandler
                    .wrapLines(
                            e,
                            this.toolTipWidth,
                            Style.EMPTY)
                    .stream()
                    .map(l -> Text.of(this.tooltipStyle + l.getString())).toList();
            result.addAll(wrapped);
        }

        return result.toArray(new Text[0]);
    }
}
