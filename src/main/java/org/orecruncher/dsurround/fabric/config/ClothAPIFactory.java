package org.orecruncher.dsurround.fabric.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.AbstractConfigScreenFactory;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class ClothAPIFactory extends AbstractConfigScreenFactory {

    private static final ResourceLocation[] BACKGROUNDS = {
            new ResourceLocation("minecraft:textures/block/cobblestone.png"),
            new ResourceLocation("minecraft:textures/block/bedrock.png"),
            new ResourceLocation("minecraft:textures/block/bricks.png"),
            new ResourceLocation("minecraft:textures/block/sandstone.png"),
            new ResourceLocation("minecraft:textures/block/stone.png"),
            new ResourceLocation("minecraft:textures/block/oak_planks.png"),
            new ResourceLocation("minecraft:textures/block/gilded_blackstone.png"),
            new ResourceLocation("minecraft:textures/block/dirt.png")
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

    public static IScreenFactory<?> createDefaultConfigScreen(Class<? extends ConfigurationData> configClass) {
        var configData = ConfigurationData.getConfig(configClass);

        ConfigOptions options = new ConfigOptions()
                .setTranslationRoot("dsurround.config")
                .setTitleStyle(Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE.getValue()))
                .setPropertyGroupStyle(Style.EMPTY.withColor(ColorPalette.GOLDENROD.getValue()))
                .setPropertyStyle(Style.EMPTY.withColor(ColorPalette.WHEAT.getValue()))
                .setTooltipStyle(Style.EMPTY.withColor(ColorPalette.SEASHELL.getValue()))
                .wrapToolTip(true);

        return new ClothAPIFactory(options, configData)::apply;
    }

    @Override
    public Screen apply(final Minecraft MinecraftClient, final Screen screen) {
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
                .startSubCategory(this.options.transformPropertyGroup(propertyGroup.getLanguageKey()))
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

        var name = this.options.transformProperty(pv.getLanguageKey());
        var tooltip = this.generateToolTip(pv);

        if (pv instanceof ConfigElement.IntegerValue v) {
            var binder = pv.<Integer>createBinder(instance);
            if (pv.useSlider()) {
                fieldBuilder = builder
                        .startIntSlider(name, binder.getValue(), v.getMinValue(), v.getMaxValue())
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
        } else if (pv instanceof ConfigElement.DoubleValue v) {
            var binder = pv.<Double>createBinder(instance);
            fieldBuilder = builder
                    .startDoubleField(name, binder.getValue())
                    .setTooltip(tooltip)
                    .setDefaultValue(binder.defaultValue())
                    .setMin(v.getMinValue())
                    .setMax(v.getMaxValue())
                    .setSaveConsumer(binder::setValue);
        } else if (pv instanceof ConfigElement.StringValue) {
            var binder = pv.<String>createBinder(instance);
            fieldBuilder = builder
                    .startStrField(name, binder.getValue())
                    .setTooltip(tooltip)
                    .setDefaultValue(binder.defaultValue())
                    .setSaveConsumer(binder::setValue);
        } else if (pv instanceof ConfigElement.BooleanValue) {
            var binder = pv.<Boolean>createBinder(instance);
            fieldBuilder = builder
                    .startBooleanToggle(name, binder.getValue())
                    .setTooltip(tooltip)
                    .setDefaultValue(binder.defaultValue())
                    .setSaveConsumer(binder::setValue);
        } else if (pv instanceof ConfigElement.EnumValue v) {
            var binder = pv.<Enum<?>>createBinder(instance);
            fieldBuilder = new EnumSelectorBuilder<>(builder.getResetButtonKey(), name, (Class<Enum<?>>)(v.getEnumClass()), binder.getValue())
                    .setTooltip(tooltip)
                    .setDefaultValue(binder.defaultValue())
                    .setSaveConsumer(binder::setValue);
        }

        if (fieldBuilder != null) {
            fieldBuilder.requireRestart(pv.isAnyRestartRequired());
        }

        return fieldBuilder;
    }

    private Component[] generateToolTip(ConfigElement.PropertyValue<?> pv) {
        return this.generateToolTipCollection(pv).toArray(new Component[0]);
   }
}