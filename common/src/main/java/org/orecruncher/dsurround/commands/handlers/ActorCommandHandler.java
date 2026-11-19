// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.processing.WorldActorHandler;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ActorCommandHandler {

    private static final String KEY = "dsurround.command.dsactor.";

    public static Component status() {
        var handler = ContainerManager.resolve(WorldActorHandler.class);
        var counts = Arrays.stream(ActorKind.values())
                .map(kind -> kind.getName() + " " + handler.count(kind))
                .collect(Collectors.joining(", "));
        return Component.translatable(KEY + "status", counts);
    }

    /**
     * @param seed the one to summon, or null for any (the reply names the seed picked, to summon it again)
     */
    public static Component summon(ActorKind kind, @Nullable Long seed) {
        var handler = ContainerManager.resolve(WorldActorHandler.class);
        long chosen = seed != null ? seed : Randomizer.current().nextLong();
        var summoned = GameUtils.getPlayer().map(player -> handler.summon(player, kind, chosen)).orElse(WorldActorHandler.Summoned.OFF);
        var result = switch (summoned) {
            case STARTED -> "summon.success";
            case OFF -> "summon.off";
            case NO_PLACE -> "summon.noplace";
        };
        return Component.translatable(KEY + result, kind.getName(), Long.toString(chosen));
    }
}
