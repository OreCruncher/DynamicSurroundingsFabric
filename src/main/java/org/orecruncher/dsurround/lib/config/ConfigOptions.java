package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConfigOptions {

    private String translationRoot = Strings.EMPTY;
    private String propertyGroupStyle = Strings.EMPTY;
    private String propertyValueStyle = Strings.EMPTY;
    private String tooltipStyle = Strings.EMPTY;
    private boolean stripTitle = true;

    public ConfigOptions() {

    }

    private static String strip(String txt) {
        var result = ChatFormatting.stripFormatting(txt);
        return result != null ? result : txt;
    }

    public ConfigOptions setPropertyGroupStyle(ChatFormatting... style) {
        this.propertyGroupStyle = combine(style);
        return this;
    }

    public ConfigOptions setPropertyValueStyle(ChatFormatting... style) {
        this.propertyValueStyle  = combine(style);
        return this;
    }

    public ConfigOptions setTooltipStyle(ChatFormatting... style) {
        this.tooltipStyle  = combine(style);
        return this;
    }

    private static String combine(ChatFormatting... style) {
        return Arrays.stream(style).map(Objects::toString).collect(Collectors.joining(""));
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
        String titleStyle = Strings.EMPTY;
        return Component.literal(titleStyle + txt);
    }

    public Component transformPropertyGroup(String langKey) {
        var txt = Localization.load(langKey);
        txt = strip(txt);
        return Component.literal(this.propertyGroupStyle + txt);
    }

    public Component transformProperty(String langKey) {
        var txt = Localization.load(langKey);
        txt = strip(txt);
        return Component.literal(this.propertyValueStyle + txt);
    }

    public Component[] transformTooltip(List<Component> tooltip) {
        var textHandler = GameUtils.getTextHandler();
        var result = new ArrayList<Component>(tooltip.size());
        for (var e : tooltip) {
            if (e.getStyle() != Style.EMPTY) {
                result.add(e);
            } else {
                int toolTipWidth = 300;
                var wrapped = textHandler
                        .splitLines(
                                e,
                                toolTipWidth,
                                Style.EMPTY)
                        .stream()
                        .map(l -> Component.literal(this.tooltipStyle + l.getString())).toList();
                result.addAll(wrapped);
            }
        }

        return result.toArray(new Component[0]);
    }
}
