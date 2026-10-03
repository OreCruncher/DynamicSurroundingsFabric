package org.orecruncher.dsurround.lib.config.compat;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.AbstractConfigScreenFactory;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class ClothAPIFactory extends AbstractConfigScreenFactory {

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

    private final ResourceLocation background;

    public ClothAPIFactory(ConfigOptions options, final ConfigurationData config) {
        this(options, config, null);
    }

    public ClothAPIFactory(ConfigOptions options, final ConfigurationData config, @Nullable final ResourceLocation background) {
        super(options, config);

        if (background == null) {
            var idx = Randomizer.current().nextInt(BACKGROUNDS.length);
            this.background = BACKGROUNDS[idx];
        } else {
            this.background = background;
        }
    }

    @Override
    public Screen apply(final Screen screen) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(screen)
                .setTitle(this.options.transformTitle())
                .setSavingRunnable(() -> {
                    try {
                        this.configData.save();
                    } catch (Throwable t) {
                        Library.LOGGER.error(t, "Unable to save configuration");
                    }
                });

        if (this.background != null) {
            builder.setDefaultBackgroundTexture(this.background);
        }

        generate(builder, this.configData);
        return builder.build();
    }

    protected void generate(final ConfigBuilder builder, Object instance) {
        ConfigCategory root = builder.getOrCreateCategory(this.options.transformTitle());
        final ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        var properties = this.configData.getSpecification();
        for (var prop : properties) {

            if (prop.isHidden())
                continue;

            if (prop instanceof ConfigElement.PropertyGroup group) {
                var result = this.generate(entryBuilder, group, instance);
                root.addEntry(result.build());
            } else if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(entryBuilder, pv, instance);
                if (result != null)
                    root.addEntry(result.build());
            }
        }
    }

    protected SubCategoryBuilder generate(final ConfigEntryBuilder builder, ConfigElement.PropertyGroup propertyGroup, Object instance) {
        SubCategoryBuilder categoryBuilder = builder
                .startSubCategory(this.options.transformPropertyGroup(propertyGroup.getLanguageKey(), propertyGroup.getTextStyle()))
                .setTooltip(this.options.transformTooltip(propertyGroup.getTooltip(this.options.getTooltipStyle())).toArray(new Component[0]));

        for (var prop : propertyGroup.getChildren()) {
            // Skip entries that are marked as hidden
            if (prop.isHidden())
                continue;

            // Can't have categories within categories, so we ignore the case of where a config is set up that way
            if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(builder, pv, propertyGroup.getInstance(instance));
                if (result != null)
                    categoryBuilder.add(result.build());
            }
        }

        return categoryBuilder;
    }

    @SuppressWarnings("unchecked")
    protected @Nullable FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> generate(final ConfigEntryBuilder builder, ConfigElement.PropertyValue<?> pv, Object instance) {
        FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> fieldBuilder = null;

        var name = this.options.transformProperty(pv.getLanguageKey(), pv.getTextStyle());
        var tooltip = this.generateToolTip(pv);

        switch (pv) {
            case ConfigElement.IntegerValue v -> {
                var binder = pv.<Integer>createBinder(instance);
                if (v.useSlider()) {
                    fieldBuilder = builder
                            .startIntSlider(name, binder.getValue(), v.getMinValue(), v.getMaxValue())
                            .setTextGetter(i -> sliderText(Integer.toString(i)))
                            .setTooltip(tooltip)
                            .setDefaultValue(binder::defaultValue)
                            .setSaveConsumer(binder::setValue);
                } else {
                    fieldBuilder = builder
                            .startIntField(name, binder.getValue())
                            .setTooltip(tooltip)
                            .setDefaultValue(binder.defaultValue())
                            .setMin(v.getMinValue())
                            .setMax(v.getMaxValue())
                            .setSaveConsumer(binder::setValue);
                }
            }
            case ConfigElement.DoubleValue v -> {
                var binder = pv.<Double>createBinder(instance);
                var scale = v.getSliderScale();
                if (scale != null) {
                    // Cloth has no double slider, so this is an integer slider over the positions. Cloth calls the
                    // save consumer for every entry, edited or not, and a value between positions (from a hand-edited
                    // file) shows at the nearest one: only write it if the slider was moved off that position, so
                    // saving doesn't change values nobody touched.
                    fieldBuilder = builder
                            .startIntSlider(name, scale.indexOf(binder.getValue()), 0, scale.lastIndex())
                            .setTextGetter(i -> sliderText(scale.format(i)))
                            .setTooltip(tooltip)
                            .setDefaultValue(() -> scale.indexOf(binder.defaultValue()))
                            .setSaveConsumer(i -> {
                                if (i != scale.indexOf(binder.getValue()))
                                    binder.setValue(scale.valueAt(i));
                            });
                } else {
                    fieldBuilder = builder
                            .startDoubleField(name, binder.getValue())
                            .setTooltip(tooltip)
                            .setDefaultValue(binder.defaultValue())
                            .setMin(v.getMinValue())
                            .setMax(v.getMaxValue())
                            .setSaveConsumer(binder::setValue);
                }
            }
            case ConfigElement.StringValue stringValue -> {
                var binder = pv.<String>createBinder(instance);
                fieldBuilder = builder
                        .startStrField(name, binder.getValue())
                        .setTooltip(tooltip)
                        .setDefaultValue(binder.defaultValue())
                        .setSaveConsumer(binder::setValue);
            }
            case ConfigElement.BooleanValue booleanValue -> {
                var binder = pv.<Boolean>createBinder(instance);
                fieldBuilder = builder
                        .startBooleanToggle(name, binder.getValue())
                        .setTooltip(tooltip)
                        .setDefaultValue(binder.defaultValue())
                        .setSaveConsumer(binder::setValue);
            }
            case ConfigElement.EnumValue v -> {
                var binder = pv.<Enum<?>>createBinder(instance);
                fieldBuilder = builder.startEnumSelector(name, (Class<Enum<?>>) v.getEnumClass(), binder.getValue())
                        .setTooltip(tooltip)
                        .setDefaultValue(binder.defaultValue())
                        .setSaveConsumer(binder::setValue);
            }
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
     * A slider's label. Cloth's own is "Value: %d", in English whatever the language.
     */
    private static Component sliderText(String value) {
        return Component.translatable("dsurround.config.slider.value", value);
    }

    private Component[] generateToolTip(ConfigElement.PropertyValue<?> pv) {
        return this.generateToolTipCollection(pv).toArray(new Component[0]);
   }
}