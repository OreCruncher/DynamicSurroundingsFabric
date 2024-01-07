package org.orecruncher.dsurround.lib.config.clothapi;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.gui.controllers.cycling.EnumController;
import dev.isxander.yacl3.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl3.gui.controllers.slider.IntegerSliderController;
import dev.isxander.yacl3.gui.controllers.string.number.DoubleFieldController;
import dev.isxander.yacl3.gui.controllers.string.number.IntegerFieldController;
import dev.isxander.yacl3.gui.controllers.string.StringController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiFunction;

public class ConfigScreenFactory implements BiFunction<Minecraft, Screen, Screen> {

    private static final Style STYLE_RESTART = Style.EMPTY.withColor(ColorPalette.RED);
    private static final Component CLIENT_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.clientRestartRequired").withStyle(STYLE_RESTART);
    private static final Component WORLD_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.worldRestartRequired").withStyle(STYLE_RESTART);
    private static final Component ASSET_RELOAD_REQUIRED = Component.translatable("dsurround.config.tooltip.assetReloadRequired").withStyle(STYLE_RESTART);

    private final ConfigOptions options;
    private final ConfigurationData configData;

    private ConfigScreenFactory(ConfigOptions options, final ConfigurationData config) {
        this.options = options;
        this.configData = config;
    }

    public static Screen createDefaultConfigScreen(Screen parent) {
        ConfigOptions options = new ConfigOptions()
                .setTranslationRoot("dsurround.config")
                .setTitleStyle(Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE.getValue()))
                .setPropertyGroupStyle(Style.EMPTY.withColor(ColorPalette.GOLDENROD.getValue()))
                .setPropertyStyle(Style.EMPTY.withColor(ColorPalette.WHEAT.getValue()))
                .setTooltipStyle(Style.EMPTY.withColor(ColorPalette.SEASHELL.getValue()));

        return new ConfigScreenFactory(options, Client.Config).apply(GameUtils.getMC(), parent);
    }

    @Override
    public Screen apply(final Minecraft MinecraftClient, final Screen screen) {
        var builder = YetAnotherConfigLib.createBuilder();

        this.generate(builder, this.configData);

        return builder
                .title(this.options.transformTitle())
                .save(() -> {
                    try {
                        this.configData.save();
                    } catch (Throwable t) {
                        Library.getLogger().error(t, "Unable to save configuration");
                    }
                })
                .build()
                .generateScreen(screen);
    }

    protected void generate(final YetAnotherConfigLib.Builder builder, Object instance) {
        var categoryBuilder = ConfigCategory.createBuilder();
        categoryBuilder.name(this.options.transformTitle());

        var properties = this.configData.getSpecification();
        for (var prop : properties) {

            if (prop.isHidden())
                continue;

            if (prop instanceof ConfigElement.PropertyGroup group) {
                var result = this.generate(group, instance);
                categoryBuilder.group(result);
            }
        }
        builder.category(categoryBuilder.build());
    }

    protected OptionGroup generate(ConfigElement.PropertyGroup propertyGroup, Object instance) {
        var tipBuilder = OptionDescription.createBuilder().text(this.options.transformTooltip(propertyGroup.getTooltip(this.options.getTooltipStyle()))).build();
        var groupBuilder = OptionGroup.createBuilder()
                .name(this.options.transformPropertyGroup(propertyGroup.getLanguageKey()))
                .description(tipBuilder)
                .collapsed(true);

        for (var prop : propertyGroup.getChildren()) {
            // Skip entries that are marked as hidden
            if (prop.isHidden())
                continue;

            // Can't have categories within categories, so we ignore the case of where a config is set up that way
            if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                this.generate(pv, propertyGroup.getInstance(instance))
                        .ifPresent(groupBuilder::option);
            }
        }

        return groupBuilder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Optional<Option<?>> generate(ConfigElement.PropertyValue<?> pv, Object instance) {
        Option.Builder<?> fieldBuilder = null;

        if (pv instanceof ConfigElement.IntegerValue v) {
            var intBuilder = Option.<Integer>createBuilder().binding(pv.createBinder(instance));
            if (pv.useSlider()) {
                intBuilder.customController(opt -> new IntegerSliderController(opt, v.getMinValue(), v.getMaxValue(), 1));
            } else {
                intBuilder.customController(IntegerFieldController::new);
            }
            fieldBuilder = intBuilder;
        } else if (pv instanceof ConfigElement.DoubleValue v) {
            var doubleBuilder = Option.<Double>createBuilder().binding(pv.createBinder(instance));
            if (pv.useSlider()) {
                doubleBuilder.customController(opt -> new DoubleSliderController(opt, v.getMinValue(), v.getMaxValue(), 0.01D));
            } else {
                doubleBuilder.customController(opt -> new DoubleFieldController(opt, v.getMinValue(), v.getMaxValue()));
            }
            fieldBuilder = doubleBuilder;
        } else if (pv instanceof ConfigElement.StringValue) {
            fieldBuilder = Option.<String>createBuilder()
                    .binding(pv.createBinder(instance))
                    .customController(StringController::new);
        } else if (pv instanceof ConfigElement.BooleanValue) {
            fieldBuilder = Option.<Boolean>createBuilder()
                    .binding(pv.createBinder(instance))
                    .customController(opt -> BooleanControllerBuilder.create(opt).formatValue(state -> state ? Component.translatable("gui.yes") : Component.translatable("gui.no")).coloured(true).build());
        } else if (pv instanceof ConfigElement.EnumValue v) {
            fieldBuilder = Option.<Enum>createBuilder()
                    .binding(pv.createBinder(instance))
                    .customController(opt -> new EnumController(opt, v.getEnumClass()));
        }

        if (fieldBuilder != null) {

            var name = this.options.transformProperty(pv.getLanguageKey());
            var toolTip = this.generateToolTip(pv);

            fieldBuilder.name(name);
            fieldBuilder.description(toolTip);

            if (pv.isRestartRequired())
                fieldBuilder.flag(OptionFlag.GAME_RESTART);
            if (pv.isAssetReloadRequired())
                fieldBuilder.flag(OptionFlag.ASSET_RELOAD);

            return Optional.of(fieldBuilder.build());
        }

        return Optional.empty();
    }

    protected OptionDescription generateToolTip(ConfigElement.PropertyValue<?> pv) {
        var toolTipEntries = new ArrayList<Component>();
        toolTipEntries.add(this.options.transformTooltip(pv.getTooltip(this.options.getTooltipStyle())));
        toolTipEntries.add(Component.empty());
        toolTipEntries.add(pv.getDefaultValueTooltip());

        if (pv instanceof ConfigElement.IRangeTooltip rt && rt.hasRange())
            toolTipEntries.add(rt.getRangeTooltip());

        if (pv.isRestartRequired()) {
            toolTipEntries.add(Component.empty());
            toolTipEntries.add(CLIENT_RESTART_REQUIRED);
        } else if (pv.isWorldRestartRequired()) {
            toolTipEntries.add(Component.empty());
            toolTipEntries.add(WORLD_RESTART_REQUIRED);
        } else if (pv.isAssetReloadRequired()) {
            toolTipEntries.add(Component.empty());
            toolTipEntries.add(ASSET_RELOAD_REQUIRED);
        }

        return OptionDescription.createBuilder().text(toolTipEntries).build();
    }
}