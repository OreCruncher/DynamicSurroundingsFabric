package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.mixinutils.OptionalHook;

import java.util.ArrayList;
import java.util.List;

public class ConflictsCommandHandler {

    /**
     * A message for each feature turned off for another mod in this session, whether it stands aside for the mod or
     * the mod changed the game code it hooks, after a heading; or one saying there are none
     */
    public static List<Component> execute() {
        var messages = new ArrayList<Component>();
        for (var conflict : FeatureConflict.values())
            conflict.blockingMod().ifPresent(mod -> messages.add(Component.translatable("dsurround.command.dsconflicts.entry",
                    Component.translatable(conflict.translationKey()), mod.installedId().orElseThrow())));
        for (var hook : OptionalHook.values())
            if (hook.status() == OptionalHook.Status.MISSING)
                messages.add(Component.translatable("dsurround.command.dsconflicts.hook", Component.translatable(hook.translationKey())));

        if (messages.isEmpty())
            return List.of(Component.translatable("dsurround.command.dsconflicts.none"));
        messages.addFirst(Component.translatable("dsurround.command.dsconflicts.header"));
        return messages;
    }
}
