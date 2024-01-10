package org.orecruncher.dsurround.lib.config.factories;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;

public abstract class AbstractConfigScreenFactory implements BiFunction<Minecraft, Screen, Screen> {

    private static final Style STYLE_RESTART = Style.EMPTY.withColor(ColorPalette.RED);
    private static final Component CLIENT_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.clientRestartRequired").withStyle(STYLE_RESTART);
    private static final Component WORLD_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.worldRestartRequired").withStyle(STYLE_RESTART);
    private static final Component ASSET_RELOAD_REQUIRED = Component.translatable("dsurround.config.tooltip.assetReloadRequired").withStyle(STYLE_RESTART);
    private static final Component EMPTY_LINE = Component.literal(" ");

    protected final ConfigOptions options;
    protected final ConfigurationData configData;

    public AbstractConfigScreenFactory(ConfigOptions options, final ConfigurationData config) {
        this.options = options;
        this.configData = config;
    }

    protected Collection<Component> generateToolTipCollection(ConfigElement.PropertyValue<?> pv) {
        var toolTipEntries = new ArrayList<Component>();
        toolTipEntries.add(this.options.transformTooltip(pv.getTooltip(this.options.getTooltipStyle())));
        toolTipEntries.add(EMPTY_LINE);
        toolTipEntries.add(pv.getDefaultValueTooltip());

        if (pv instanceof ConfigElement.IRangeTooltip rt && rt.hasRange())
            toolTipEntries.add(rt.getRangeTooltip());

        if (pv.isRestartRequired()) {
            toolTipEntries.add(EMPTY_LINE);
            toolTipEntries.add(CLIENT_RESTART_REQUIRED);
        } else if (pv.isWorldRestartRequired()) {
            toolTipEntries.add(EMPTY_LINE);
            toolTipEntries.add(WORLD_RESTART_REQUIRED);
        } else if (pv.isAssetReloadRequired()) {
            toolTipEntries.add(EMPTY_LINE);
            toolTipEntries.add(ASSET_RELOAD_REQUIRED);
        }

        return toolTipEntries;
    }

    @Override
    public abstract Screen apply(Minecraft minecraft, Screen screen);
}
