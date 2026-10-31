package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.List;

/**
 * /dsconfig preset lists the configuration's presets; /dsconfig preset name applies one and saves.
 */
public class ConfigCommandHandler {

    private static final Style NAME_STYLE = Style.EMPTY.withColor(ColorPalette.GOLDENROD);

    public static Component presets(ConfigurationData config) {
        return Component.translatable("dsurround.command.dsconfig.preset.list", list(config));
    }

    public static Component preset(ConfigurationData config, String id) {
        var preset = config.getPreset(id);
        if (preset.isEmpty())
            return Component.translatable("dsurround.command.dsconfig.preset.unknown", id, list(config));

        var name = name(config, preset.get());
        int problems = config.applyPreset(preset.get());
        return problems == 0
                ? Component.translatable("dsurround.command.dsconfig.preset.success", name)
                : Component.translatable("dsurround.command.dsconfig.preset.problems", name, problems);
    }

    /**
     * The presets' ids by group ("Features: a, b; Performance: c"), each showing its description when hovered and
     * putting its command in the chat box when clicked.
     */
    private static Component list(ConfigurationData config) {
        var list = Component.empty();
        var presets = config.getPresets();
        var groups = ConfigPreset.groups(presets);
        for (int g = 0; g < groups.size(); g++) {
            var group = groups.get(g);
            if (g > 0)
                list.append("; ");
            list.append(ConfigPreset.displayGroup(config, group) + ": ");
            list.append(ids(config, presets.stream().filter(p -> p.group().equals(group)).toList()));
        }
        return list;
    }

    private static Component ids(ConfigurationData config, List<ConfigPreset> presets) {
        var list = Component.empty();
        var first = true;
        for (var preset : presets) {
            if (!first)
                list.append(", ");
            first = false;
            var hover = name(config, preset).append("\n").append(preset.displayDescription(config));
            list.append(Component.literal(preset.id()).withStyle(NAME_STYLE
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/dsconfig preset " + preset.id()))));
        }
        return list;
    }

    private static MutableComponent name(ConfigurationData config, ConfigPreset preset) {
        return Component.literal(preset.displayName(config)).withStyle(NAME_STYLE);
    }
}
