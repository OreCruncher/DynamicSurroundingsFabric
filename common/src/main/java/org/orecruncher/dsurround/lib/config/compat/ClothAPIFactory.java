package org.orecruncher.dsurround.lib.config.compat;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.Localization;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Cloth Config screen for a configuration from its specification.
 */
public class ClothAPIFactory implements IScreenFactory<Screen> {

    private static final ResourceLocation[] BACKGROUNDS = {
            ResourceLocation.parse("minecraft:textures/block/cobblestone.png"),
            ResourceLocation.parse("minecraft:textures/block/bedrock.png"),
            ResourceLocation.parse("minecraft:textures/block/bricks.png"),
            ResourceLocation.parse("minecraft:textures/block/sandstone.png"),
            ResourceLocation.parse("minecraft:textures/block/stone.png"),
            ResourceLocation.parse("minecraft:textures/block/oak_planks.png"),
            ResourceLocation.parse("minecraft:textures/block/gilded_blackstone.png"),
            ResourceLocation.parse("minecraft:textures/block/dirt.png")
    };

    private static final Style TITLE_STYLE = Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE);
    private static final Style GROUP_STYLE = Style.EMPTY.withColor(ColorPalette.GOLDENROD);
    private static final Style PROPERTY_STYLE = Style.EMPTY.withColor(ColorPalette.WHEAT);
    private static final Style TOOLTIP_STYLE = Style.EMPTY.withColor(ColorPalette.SEASHELL);
    private static final Style RESTART_STYLE = Style.EMPTY.withColor(ColorPalette.RED);
    private static final int TOOLTIP_WIDTH = 300;

    private static final Component CLIENT_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.clientRestartRequired").withStyle(RESTART_STYLE);
    private static final Component WORLD_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.worldRestartRequired").withStyle(RESTART_STYLE);
    // Use a string with a single space as an empty line. Some config UI frameworks elide Component.empty() entries
    // and the tooltip logic uses empty lines as part of its formatting.
    private static final Component EMPTY_LINE = Component.literal(" ");

    private final ConfigurationData configData;
    private final ResourceLocation background;

    public ClothAPIFactory(ConfigurationData config) {
        this.configData = config;
        this.background = BACKGROUNDS[Randomizer.current().nextInt(BACKGROUNDS.length)];
    }

    @Override
    public Screen create(Screen parent) {
        var title = text(this.configData.getTranslationRoot() + ".title", TITLE_STYLE);
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(title)
                .setDefaultBackgroundTexture(this.background)
                .setSavingRunnable(() -> {
                    try {
                        this.configData.save();
                    } catch (Throwable t) {
                        Library.LOGGER.error(t, "Unable to save configuration");
                    }
                });

        ConfigCategory root = builder.getOrCreateCategory(title);
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        for (var prop : this.configData.getSpecification()) {
            if (prop instanceof ConfigElement.PropertyGroup group) {
                root.addEntry(this.generate(entryBuilder, group, this.configData).build());
            } else if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(entryBuilder, pv, this.configData);
                if (result != null)
                    root.addEntry(result.build());
            }
        }
        return builder.build();
    }

    private SubCategoryBuilder generate(ConfigEntryBuilder builder, ConfigElement.PropertyGroup propertyGroup, Object instance) {
        SubCategoryBuilder categoryBuilder = builder
                .startSubCategory(text(propertyGroup.getLanguageKey(), styleOr(propertyGroup.getTextStyle(), GROUP_STYLE)))
                .setTooltip(wrap(propertyGroup.getTooltip(TOOLTIP_STYLE)).toArray(new Component[0]));

        // Can't have categories within categories, so we ignore the case of where a config is set up that way
        for (var prop : propertyGroup.getChildren()) {
            if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(builder, pv, propertyGroup.getInstance(instance));
                if (result != null)
                    categoryBuilder.add(result.build());
            }
        }

        return categoryBuilder;
    }

    @SuppressWarnings("unchecked")
    private @Nullable FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> generate(ConfigEntryBuilder builder, ConfigElement.PropertyValue<?> pv, Object instance) {
        FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> fieldBuilder = null;

        var name = text(pv.getLanguageKey(), styleOr(pv.getTextStyle(), PROPERTY_STYLE));
        var tooltip = tooltip(pv);

        switch (pv) {
            case ConfigElement.IntegerValue v -> {
                if (v.useSlider()) {
                    fieldBuilder = builder
                            .startIntSlider(name, v.getValue(instance), v.getMinValue(), v.getMaxValue())
                            .setTextGetter(i -> sliderText(Integer.toString(i)))
                            .setTooltip(tooltip)
                            .setDefaultValue(v::defaultValue)
                            .setSaveConsumer(value -> v.setValue(instance, value));
                } else {
                    fieldBuilder = builder
                            .startIntField(name, v.getValue(instance))
                            .setTooltip(tooltip)
                            .setDefaultValue(v.defaultValue())
                            .setMin(v.getMinValue())
                            .setMax(v.getMaxValue())
                            .setSaveConsumer(value -> v.setValue(instance, value));
                }
            }
            case ConfigElement.DoubleValue v -> {
                var scale = v.getSliderScale();
                if (scale != null) {
                    // Cloth has no double slider, so this is an integer slider over the positions. Cloth calls the
                    // save consumer for every entry, edited or not, and a value between positions (from a hand-edited
                    // file) shows at the nearest one: only write it if the slider was moved off that position, so
                    // saving doesn't change values nobody touched.
                    fieldBuilder = builder
                            .startIntSlider(name, scale.indexOf(v.getValue(instance)), 0, scale.lastIndex())
                            .setTextGetter(i -> sliderText(scale.format(i)))
                            .setTooltip(tooltip)
                            .setDefaultValue(() -> scale.indexOf(v.defaultValue()))
                            .setSaveConsumer(i -> {
                                if (i != scale.indexOf(v.getValue(instance)))
                                    v.setValue(instance, scale.valueAt(i));
                            });
                } else {
                    fieldBuilder = builder
                            .startDoubleField(name, v.getValue(instance))
                            .setTooltip(tooltip)
                            .setDefaultValue(v.defaultValue())
                            .setMin(v.getMinValue())
                            .setMax(v.getMaxValue())
                            .setSaveConsumer(value -> v.setValue(instance, value));
                }
            }
            case ConfigElement.StringValue v -> fieldBuilder = builder
                    .startStrField(name, v.getValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.defaultValue())
                    .setSaveConsumer(value -> v.setValue(instance, value));
            case ConfigElement.BooleanValue v -> fieldBuilder = builder
                    .startBooleanToggle(name, v.getValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.defaultValue())
                    .setSaveConsumer(value -> v.setValue(instance, value));
            case ConfigElement.EnumValue v -> fieldBuilder = builder
                    .startEnumSelector(name, (Class<Enum<?>>) v.getEnumClass(), v.getValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.defaultValue())
                    .setSaveConsumer(value -> v.setValue(instance, value));
            default -> {
            }
        }

        // Cloth's restart prompt asks to exit Minecraft, so only use it when that is what's needed. A world
        // restart (leave and rejoin) is explained in the tooltip instead.
        if (fieldBuilder != null) {
            fieldBuilder.requireRestart(pv.isClientRestartRequired());
        }

        return fieldBuilder;
    }

    /**
     * A property's tooltip: its description, default value, range, and whether a restart is needed.
     */
    private static Component[] tooltip(ConfigElement.PropertyValue<?> pv) {
        var lines = wrap(pv.getTooltip(TOOLTIP_STYLE));
        lines.add(EMPTY_LINE);
        lines.add(pv.getDefaultValueTooltip());

        if (pv instanceof ConfigElement.IRangeTooltip rt && rt.hasRange())
            lines.add(rt.getRangeTooltip());

        if (pv.isClientRestartRequired()) {
            lines.add(EMPTY_LINE);
            lines.add(CLIENT_RESTART_REQUIRED);
        } else if (pv.isWorldRestartRequired()) {
            lines.add(EMPTY_LINE);
            lines.add(WORLD_RESTART_REQUIRED);
        }

        return lines.toArray(new Component[0]);
    }

    /**
     * The text split into lines that fit a tooltip. The list can be added to.
     */
    private static List<Component> wrap(Component text) {
        return new ArrayList<>(GuiHelpers.getTrimmedTextCollection(text, TOOLTIP_WIDTH, TOOLTIP_STYLE));
    }

    private static Component text(String key, Style style) {
        return Component.literal(Localization.load(key)).withStyle(style);
    }

    /**
     * {@code style}, or {@code fallback} if it is empty (the property has no TextStyle annotation).
     */
    private static Style styleOr(Style style, Style fallback) {
        return style.isEmpty() ? fallback : style;
    }

    /**
     * A slider's label. Cloth's own is "Value: %d", in English whatever the language.
     */
    private static Component sliderText(String value) {
        return Component.translatable("dsurround.config.slider.value", value);
    }
}
