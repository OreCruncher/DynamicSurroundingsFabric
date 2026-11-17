// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.compat.ShaderPackFeature;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.mixinutils.OptionalHook;

import java.util.ArrayList;
import java.util.List;

public class ConflictsCommandHandler {

    /**
     * A message for each feature turned off for another mod in this session, whether it stands aside for the mod or
     * the mod changed the game code it hooks, after a heading; then, while a shader pack is in use, the effects it
     * keeps off, after a heading of their own; or one message saying there are none
     */
    public static List<Component> execute() {
        var messages = new ArrayList<Component>();
        for (var conflict : FeatureConflict.values())
            conflict.blockingMod().ifPresent(mod -> messages.add(Component.translatable("dsurround.command.dsconflicts.entry",
                    Component.translatable(conflict.translationKey()), mod.installedId().orElseThrow())));
        for (var hook : OptionalHook.values())
            if (hook.status() == OptionalHook.Status.MISSING)
                messages.add(Component.translatable("dsurround.command.dsconflicts.hook", Component.translatable(hook.translationKey())));
        if (!messages.isEmpty())
            messages.addFirst(Component.translatable("dsurround.command.dsconflicts.header"));

        messages.addAll(shaderPackMessages(IrisCompat.isShaderPackInUse(), Client.Config));

        if (messages.isEmpty())
            return List.of(Component.translatable("dsurround.command.dsconflicts.none"));
        return messages;
    }

    /**
     * While a shader pack is in use, a heading and a message for each effect switched on in the configuration that
     * the pack keeps off (see {@link ShaderPackFeature}); nothing otherwise
     */
    static List<Component> shaderPackMessages(boolean shaderPackInUse, Configuration config) {
        if (!shaderPackInUse)
            return List.of();
        var messages = new ArrayList<Component>();
        messages.add(Component.translatable("dsurround.command.dsconflicts.shader_pack"));
        for (var feature : ShaderPackFeature.switchedOn(config))
            messages.add(Component.translatable("dsurround.command.dsconflicts.shader_pack_entry", Component.translatable(feature.translationKey())));
        return messages;
    }
}
