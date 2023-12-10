package org.orecruncher.dsurround.lib.config.clothapi;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.function.BiFunction;

@Environment(EnvType.CLIENT)
public class ClothAPIFactory implements BiFunction<MinecraftClient, Screen, Screen> {

    private static final Identifier[] BACKGROUNDS = {
            new Identifier("minecraft:textures/block/cobblestone.png"),
            new Identifier("minecraft:textures/block/bedrock.png"),
            new Identifier("minecraft:textures/block/bricks.png"),
            new Identifier("minecraft:textures/block/sandstone.png"),
            new Identifier("minecraft:textures/block/stone.png"),
            new Identifier("minecraft:textures/block/oak_planks.png"),
            new Identifier("minecraft:textures/block/gilded_blackstone.png"),
            new Identifier("minecraft:textures/block/dirt.png")
    };

    private final Identifier background;
    private final ConfigOptions options;
    private final ConfigurationData configData;

    public ClothAPIFactory(ConfigOptions options, final ConfigurationData config) {
        this(options, config, null);
    }

    public ClothAPIFactory(ConfigOptions options, final ConfigurationData config, @Nullable final Identifier background) {
        this.options = options;
        this.configData = config;

        if (background == null) {
            var idx = XorShiftRandom.current().nextInt(BACKGROUNDS.length);
            this.background = BACKGROUNDS[idx];
        } else {
            this.background = background;
        }
    }

    @Override
    public Screen apply(final MinecraftClient MinecraftClient, final Screen screen) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(screen)
                .setTitle(this.options.transformTitle())
                .setSavingRunnable(() -> {
                    try {
                        this.configData.save();
                    } catch (Throwable t) {
                        Client.LOGGER.error(t, "Unable to save configuration");
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
            } else if (prop instanceof ConfigElement.PropertyValue pv) {
                var result = this.generate(entryBuilder, pv, instance);
                if (result != null)
                    root.addEntry(result.build());
            }
        }
    }

    protected SubCategoryBuilder generate(final ConfigEntryBuilder builder, ConfigElement.PropertyGroup propertyGroup, Object instance) {
        SubCategoryBuilder categoryBuilder = builder
                .startSubCategory(this.options.transformPropertyGroup(propertyGroup.getElementNameKey()))
                .setTooltip(this.options.transformTooltip(propertyGroup.getTooltip()));

        for (var prop : propertyGroup.getChildren()) {
            // Skip entries that are marked as hidden
            if (prop.isHidden())
                continue;

            // Can't have categories within categories, so we ignore the case of where a config is set up that way
            if (prop instanceof ConfigElement.PropertyValue pv) {
                var result = this.generate(builder, pv, propertyGroup.getInstance(instance));
                if (result != null)
                    categoryBuilder.add(result.build());
            }
        }

        return categoryBuilder;
    }

    protected @Nullable FieldBuilder<?, ? extends AbstractConfigListEntry<?>> generate(final ConfigEntryBuilder builder, ConfigElement.PropertyValue<?> pv, Object instance) {
        FieldBuilder<?, ? extends AbstractConfigListEntry<?>> fieldBuilder = null;

        var name = this.options.transformProperty(pv.getElementNameKey());
        var tooltip = this.options.transformTooltip(pv.getTooltip());

        if (pv instanceof ConfigElement.IntegerValue v) {
            if (pv.useSlider()) {
                fieldBuilder = builder
                        .startIntSlider(name, v.getCurrentValue(instance), v.getMinValue(), v.getMaxValue())
                        .setTooltip(tooltip)
                        .setDefaultValue(v::getDefaultValue)
                        .setSaveConsumer(data -> v.setCurrentValue(instance, data));
            } else {
                fieldBuilder = builder
                        .startIntField(name, v.getCurrentValue(instance))
                        .setTooltip(tooltip)
                        .setDefaultValue(v.getDefaultValue())
                        .setMin(v.getMinValue())
                        .setMax(v.getMaxValue())
                        .setSaveConsumer(data -> v.setCurrentValue(instance, data));
            }
        } else if (pv instanceof ConfigElement.DoubleValue v) {
            fieldBuilder = builder
                    .startDoubleField(name, v.getCurrentValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.getDefaultValue())
                    .setMin(v.getMinValue())
                    .setMax(v.getMaxValue())
                    .setSaveConsumer(data -> v.setCurrentValue(instance, data));
        } else if (pv instanceof ConfigElement.StringValue v) {
            fieldBuilder = builder
                    .startStrField(name, v.getCurrentValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.getDefaultValue())
                    .setSaveConsumer(data -> v.setCurrentValue(instance, data));
        } else if (pv instanceof ConfigElement.BooleanValue v) {
            fieldBuilder = builder
                    .startBooleanToggle(name, v.getCurrentValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.getDefaultValue())
                    .setSaveConsumer(data -> v.setCurrentValue(instance, data));
        } else if (pv instanceof ConfigElement.EnumValue v) {
            fieldBuilder = new EnumSelectorBuilder(builder.getResetButtonKey(), name, v.getEnumClass(), v.getCurrentValue(instance))
                    .setTooltip(tooltip)
                    .setDefaultValue(v.getDefaultValue())
                    .setSaveConsumer(data -> v.setCurrentValue(instance, data));
        }

        if (fieldBuilder != null) {
            fieldBuilder.requireRestart(pv.isRestartRequired());
        }

        return fieldBuilder;
    }
}