package org.orecruncher.dsurround.lib.config.compat;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.text.Localization;
import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.lib.gui.TooltipDelay;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Builds a Cloth Config screen for a configuration from its specification.
 */
public class ClothAPIFactory implements IScreenFactory<Screen> {

    private static final Identifier[] BACKGROUNDS = {
            Identifier.parse("minecraft:textures/block/cobblestone.png"),
            Identifier.parse("minecraft:textures/block/bedrock.png"),
            Identifier.parse("minecraft:textures/block/bricks.png"),
            Identifier.parse("minecraft:textures/block/sandstone.png"),
            Identifier.parse("minecraft:textures/block/stone.png"),
            Identifier.parse("minecraft:textures/block/oak_planks.png"),
            Identifier.parse("minecraft:textures/block/gilded_blackstone.png"),
            Identifier.parse("minecraft:textures/block/dirt.png")
    };

    private static final Style TITLE_STYLE = Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE);
    private static final Style GROUP_STYLE = Style.EMPTY.withColor(ColorPalette.GOLDENROD);
    private static final Style PROPERTY_STYLE = Style.EMPTY.withColor(ColorPalette.WHEAT);
    private static final Style TOOLTIP_STYLE = Style.EMPTY.withColor(ColorPalette.SEASHELL);
    private static final Style RESTART_STYLE = Style.EMPTY.withColor(ColorPalette.RED);
    private static final Style EXPERIMENTAL_STYLE = Style.EMPTY.withColor(ColorPalette.ORCHID);
    private static final Style LINK_STYLE = Style.EMPTY.withColor(ColorPalette.MC_AQUA).withUnderlined(true);
    // A chain link drawn by the mod's own font (assets/dsurround/font/icons.json), so it is text Cloth draws with
    // the name, and resource packs can replace it.
    private static final Style LINK_ICON_STYLE = Style.EMPTY.withColor(ColorPalette.MC_AQUA).withFont(new FontDescription.Resource(Identifier.parse("dsurround:icons")));
    private static final String LINK_ICON = "\uE000";
    private static final int TOOLTIP_WIDTH = 300;

    // The documentation's page for the config file. Each group has a section headed with its name in the file, which
    // ReadTheDocs turns into an anchor in lower case.
    private static final String DOCS_URL = "https://dynamic-surroundings.readthedocs.io/en/latest/config/modconfiguration.html";

    private static final Component CLIENT_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.clientRestartRequired").withStyle(RESTART_STYLE);
    private static final Component WORLD_RESTART_REQUIRED = Component.translatable("dsurround.config.tooltip.worldRestartRequired").withStyle(RESTART_STYLE);
    private static final Component EXPERIMENTAL = Component.translatable("dsurround.config.tooltip.experimental");
    // Use a string with a single space as an empty line. Some config UI frameworks elide Component.empty() entries
    // and the tooltip logic uses empty lines as part of its formatting.
    private static final Component EMPTY_LINE = Component.literal(" ");

    private final ConfigurationData configData;
    private final Identifier background;

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
        root.addEntry(docsLink(entryBuilder, "dsurround.config.docs.page", DOCS_URL));
        // A row of buttons for each group of presets (features, performance), in the order the presets first name them
        var presets = this.configData.getPresets();
        var groups = ConfigPreset.groups(presets);
        var font = Minecraft.getInstance().font;
        int nameWidth = groups.stream().mapToInt(g -> font.width(ConfigPreset.displayGroup(this.configData, g))).max().orElse(0);
        for (var group : groups)
            root.addEntry(this.presetButtons(group, presets.stream().filter(p -> p.group().equals(group)).toList(), nameWidth, parent));
        for (var prop : this.configData.getSpecification()) {
            if (prop instanceof ConfigElement.PropertyGroup group) {
                root.addEntry(this.generate(entryBuilder, group, this.configData, docsUrl(group)).build());
            } else if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(entryBuilder, pv, this.configData);
                if (result != null)
                    root.addEntry(result.build());
            }
        }
        return builder.build();
    }

    /**
     * A group's subcategory, with its settings and the groups within it, in the order they are declared.
     *
     * @param docsUrl where the group's name links to: the documentation has a section for each top-level group only,
     *                so a group within one links to its top-level group's section
     */
    private SubCategoryBuilder generate(ConfigEntryBuilder builder, ConfigElement.PropertyGroup propertyGroup, Object instance, String docsUrl) {
        // The name ends with a link to the group's documentation. Cloth doesn't act on clicks in a group's name, so
        // MixinClothCategoryLabelWidget does.
        var name = text(propertyGroup.getLanguageKey(), nameStyle(propertyGroup, GROUP_STYLE))
                .append(" ")
                .append(Component.literal(LINK_ICON).withStyle(linkStyle(LINK_ICON_STYLE, docsUrl)));
        SubCategoryBuilder categoryBuilder = builder
                .startSubCategory(name)
                .setTooltipSupplier(delayed(description(propertyGroup).toArray(new Component[0])));

        var groupInstance = propertyGroup.getInstance(instance);
        for (var prop : propertyGroup.getChildren()) {
            if (prop instanceof ConfigElement.PropertyGroup group) {
                categoryBuilder.add(this.generate(builder, group, groupInstance, docsUrl).build());
            } else if (prop instanceof ConfigElement.PropertyValue<?> pv) {
                var result = this.generate(builder, pv, groupInstance);
                if (result != null)
                    categoryBuilder.add(result.build());
            }
        }

        return categoryBuilder;
    }

    @SuppressWarnings("unchecked")
    private @Nullable FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> generate(ConfigEntryBuilder builder, ConfigElement.PropertyValue<?> pv, Object instance) {
        FieldBuilder<?, ? extends AbstractConfigListEntry<?>, ?> fieldBuilder = null;

        var name = text(pv.getLanguageKey(), nameStyle(pv, PROPERTY_STYLE));
        var tooltip = tooltip(pv);

        switch (pv) {
            case ConfigElement.IntegerValue v -> {
                if (v.useSlider()) {
                    fieldBuilder = builder
                            .startIntSlider(name, v.getValue(instance), v.getMinValue(), v.getMaxValue())
                            .setTextGetter(i -> sliderText(Integer.toString(i)))
                            .setTooltipSupplier(delayed(tooltip))
                            .setDefaultValue(v::defaultValue)
                            .setSaveConsumer(value -> v.setValue(instance, value));
                } else {
                    fieldBuilder = builder
                            .startIntField(name, v.getValue(instance))
                            .setTooltipSupplier(delayed(tooltip))
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
                            .setTooltipSupplier(delayed(tooltip))
                            .setDefaultValue(() -> scale.indexOf(v.defaultValue()))
                            .setSaveConsumer(i -> {
                                if (i != scale.indexOf(v.getValue(instance)))
                                    v.setValue(instance, scale.valueAt(i));
                            });
                } else {
                    fieldBuilder = builder
                            .startDoubleField(name, v.getValue(instance))
                            .setTooltipSupplier(delayed(tooltip))
                            .setDefaultValue(v.defaultValue())
                            .setMin(v.getMinValue())
                            .setMax(v.getMaxValue())
                            .setSaveConsumer(value -> v.setValue(instance, value));
                }
            }
            case ConfigElement.StringValue v -> fieldBuilder = builder
                    .startStrField(name, v.getValue(instance))
                    .setTooltipSupplier(delayed(tooltip))
                    .setDefaultValue(v.defaultValue())
                    .setSaveConsumer(value -> v.setValue(instance, value));
            case ConfigElement.BooleanValue v -> fieldBuilder = builder
                    .startBooleanToggle(name, v.getValue(instance))
                    .setTooltipSupplier(delayed(tooltip))
                    .setDefaultValue(v.defaultValue())
                    .setSaveConsumer(value -> v.setValue(instance, value));
            case ConfigElement.EnumValue v -> fieldBuilder = builder
                    .startEnumSelector(name, (Class<Enum<?>>) v.getEnumClass(), v.getValue(instance))
                    .setEnumNameProvider(ConfigElement.EnumValue::displayName)
                    .setTooltipSupplier(delayed(tooltip))
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
     * A row with a button for each of a group's presets, named for the group.
     */
    private AbstractConfigListEntry<?> presetButtons(String group, List<ConfigPreset> presets, int nameWidth, Screen parent) {
        var choices = new ArrayList<PresetButtonsEntry.Choice>();
        for (var preset : presets) {
            var label = Component.literal(preset.displayName(this.configData));
            var description = Component.literal(preset.displayDescription(this.configData)).withStyle(TOOLTIP_STYLE);
            var tooltip = wrap(description).toArray(new Component[0]);
            choices.add(new PresetButtonsEntry.Choice(label, tooltip, () -> this.confirmPreset(preset, parent)));
        }
        var key = ConfigPreset.groupLanguageKey(this.configData, group);
        var description = Localization.loadIfPresent(key + ".tooltip").orElse("");
        var tooltip = wrap(Component.literal(description).withStyle(TOOLTIP_STYLE)).toArray(new Component[0]);
        var name = Component.literal(ConfigPreset.displayGroup(this.configData, group)).withStyle(GROUP_STYLE);
        return new PresetButtonsEntry(name, tooltip, choices, nameWidth);
    }

    /**
     * Asks whether to apply a preset, listing what it would change. Cloth's entries each keep their own copy of a
     * value, and save every one, so the preset is applied to the configuration, saved, and the screen built again to
     * show it; changes not yet saved on the screen are lost, which the question says when there are any.
     */
    private void confirmPreset(ConfigPreset preset, Screen parent) {
        var current = GameUtils.getCurrentScreen().orElse(null);
        var root = this.configData.getTranslationRoot();
        var name = preset.displayName(this.configData);
        var title = Component.literal(Localization.load(root + ".presets.confirm").formatted(name));
        var changes = preset.changes(this.configData);
        var message = Component.literal(Localization.load(root + (changes.isEmpty() ? ".presets.confirm.none" : ".presets.confirm.message")));
        if (current instanceof AbstractConfigScreen screen && screen.isEdited())
            message.append("\n\n").append(Component.literal(Localization.load(root + ".presets.confirm.unsaved")).withStyle(RESTART_STYLE));

        GameUtils.setScreen(new PresetConfirmScreen(title, message, changes.isEmpty() ? null : changeList(changes), apply -> {
            if (!apply) {
                GameUtils.setScreen(current);
                return;
            }
            try {
                this.configData.applyPreset(preset);
            } catch (Throwable t) {
                Library.LOGGER.error(t, "Unable to save configuration");
            }
            GameUtils.setScreen(this.create(parent));
        }));
    }

    /**
     * A preset's changes, a line each ("Name: old -> new"), under the name of the group they are in. Groups are in
     * the order the preset first lists one of their settings.
     */
    @SuppressWarnings("unchecked")
    private static Component changeList(List<ConfigPreset.Change> changes) {
        var groups = new LinkedHashMap<Optional<ConfigElement.PropertyGroup>, List<ConfigPreset.Change>>();
        for (var change : changes)
            groups.computeIfAbsent(Optional.ofNullable(change.group()), g -> new ArrayList<>()).add(change);

        // A value with a color of its own (Yes and No) keeps it; the rest take the tooltip's
        var text = Component.empty().withStyle(TOOLTIP_STYLE);
        var lines = new ArrayList<Component>();
        for (var entry : groups.entrySet()) {
            if (!lines.isEmpty())
                lines.add(Component.empty());
            entry.getKey().ifPresent(group -> lines.add(text(group.getLanguageKey(), GROUP_STYLE)));
            for (var change : entry.getValue()) {
                var property = (ConfigElement.PropertyValue<Object>) change.property();
                lines.add(Component.literal("  ")
                        .append(text(property.getLanguageKey(), PROPERTY_STYLE))
                        .append(": ")
                        .append(property.displayValue(change.from()))
                        .append(" → ")
                        .append(property.displayValue(change.to())));
            }
        }
        for (int i = 0; i < lines.size(); i++)
            text.append(i == 0 ? Component.empty() : Component.literal("\n")).append(lines.get(i));
        return text;
    }

    /**
     * A line of text that opens {@code url} when clicked, once the player confirms (Cloth asks with vanilla's link
     * screen). Hovering shows the address.
     */
    private static AbstractConfigListEntry<?> docsLink(ConfigEntryBuilder builder, String key, String url) {
        return builder.startTextDescription(Component.translatable(key).withStyle(linkStyle(LINK_STYLE, url))).build();
    }

    /**
     * {@code style} made to open {@code url} when clicked, showing the address on hover.
     */
    private static Style linkStyle(Style style, String url) {
        return style
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
    }

    /**
     * The address of a group's section in the documentation.
     */
    private static String docsUrl(ConfigElement.PropertyGroup group) {
        return DOCS_URL + "#" + group.fieldName().toLowerCase(Locale.ROOT);
    }

    /**
     * A tooltip that only shows once the mouse has rested on its entry for a moment, and not while a slider is being
     * dragged (see {@link TooltipDelay}). Each entry gets its own: Cloth asks only the entry under the mouse.
     */
    private static Supplier<Optional<Component[]>> delayed(Component[] tooltip) {
        var delay = new TooltipDelay();
        var shown = Optional.of(tooltip);
        return () -> delay.ready() ? shown : Optional.empty();
    }

    /**
     * A property's tooltip: its description, default value, range, and whether a restart is needed.
     */
    private static Component[] tooltip(ConfigElement.PropertyValue<?> pv) {
        var lines = description(pv);
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
     * An element's description for its tooltip, followed by a notice if it is experimental. The list can be added to.
     */
    private static List<Component> description(ConfigElement<?> element) {
        var lines = wrap(element.getTooltip(TOOLTIP_STYLE));
        if (element.isExperimental()) {
            lines.add(EMPTY_LINE);
            lines.addAll(wrap(EXPERIMENTAL, EXPERIMENTAL_STYLE));
        }
        return lines;
    }

    /**
     * The text split into lines that fit a tooltip. The list can be added to.
     */
    private static List<Component> wrap(Component text) {
        return wrap(text, TOOLTIP_STYLE);
    }

    /**
     * The text split into lines that fit a tooltip, each in {@code style}. The list can be added to.
     */
    private static List<Component> wrap(Component text, Style style) {
        return new ArrayList<>(GuiHelpers.getTrimmedTextCollection(text, TOOLTIP_WIDTH, style));
    }

    private static MutableComponent text(String key, Style style) {
        return Component.literal(Localization.load(key)).withStyle(style);
    }

    /**
     * The style of an element's name: its own TextStyle if it has one, otherwise the experimental color if it is
     * experimental, otherwise {@code fallback}.
     */
    private static Style nameStyle(ConfigElement<?> element, Style fallback) {
        return styleOr(element.getTextStyle(), element.isExperimental() ? EXPERIMENTAL_STYLE : fallback);
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
