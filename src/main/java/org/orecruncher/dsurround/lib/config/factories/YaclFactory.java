package org.orecruncher.dsurround.lib.config.factories;

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
import org.orecruncher.dsurround.lib.config.Binder;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.Optional;

public class YaclFactory extends AbstractConfigScreenFactory {

    private YaclFactory(ConfigOptions options, final ConfigurationData config) {
        super(options, config);
    }

    public static Screen createDefaultConfigScreen(Screen parent) {
        ConfigOptions options = new ConfigOptions()
                .setTranslationRoot("dsurround.config")
                .setTitleStyle(Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE.getValue()))
                .setPropertyGroupStyle(Style.EMPTY.withColor(ColorPalette.GOLDENROD.getValue()))
                .setPropertyStyle(Style.EMPTY.withColor(ColorPalette.WHEAT.getValue()))
                .setTooltipStyle(Style.EMPTY.withColor(ColorPalette.SEASHELL.getValue()));

        return new YaclFactory(options, Client.Config).apply(GameUtils.getMC(), parent);
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
            var intBuilder = Option.<Integer>createBuilder().binding(bindingFrom(pv, instance));
            if (pv.useSlider()) {
                intBuilder.customController(opt -> new IntegerSliderController(opt, v.getMinValue(), v.getMaxValue(), 1));
            } else {
                intBuilder.customController(IntegerFieldController::new);
            }
            fieldBuilder = intBuilder;
        } else if (pv instanceof ConfigElement.DoubleValue v) {
            var doubleBuilder = Option.<Double>createBuilder().binding(bindingFrom(pv, instance));
            if (pv.useSlider()) {
                doubleBuilder.customController(opt -> new DoubleSliderController(opt, v.getMinValue(), v.getMaxValue(), 0.01D));
            } else {
                doubleBuilder.customController(opt -> new DoubleFieldController(opt, v.getMinValue(), v.getMaxValue()));
            }
            fieldBuilder = doubleBuilder;
        } else if (pv instanceof ConfigElement.StringValue) {
            fieldBuilder = Option.<String>createBuilder()
                    .binding(bindingFrom(pv, instance))
                    .customController(StringController::new);
        } else if (pv instanceof ConfigElement.BooleanValue) {
            fieldBuilder = Option.<Boolean>createBuilder()
                    .binding(bindingFrom(pv, instance))
                    .customController(opt -> BooleanControllerBuilder.create(opt).formatValue(state -> state ? Component.translatable("gui.yes") : Component.translatable("gui.no")).coloured(true).build());
        } else if (pv instanceof ConfigElement.EnumValue v) {
            fieldBuilder = Option.<Enum>createBuilder()
                    .binding(bindingFrom(pv, instance))
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

    private OptionDescription generateToolTip(ConfigElement.PropertyValue<?> pv) {
        return OptionDescription.createBuilder().text(this.generateToolTipCollection(pv)).build();
    }

    /**
     * Wraps a property value binder into a form that YACL likes
     */
    public static <T> Binding<T> bindingFrom(ConfigElement.PropertyValue<?> pv, Object instance) {
        return new Binding<>() {

            private final Binder<T> binder = pv.createBinder(instance);

            @Override
            public void setValue(T value) {
                this.binder.setValue(value);
            }

            @Override
            public T getValue() {
                return this.binder.getValue();
            }

            @Override
            public T defaultValue() {
                return this.binder.defaultValue();
            }
        };
    }
}