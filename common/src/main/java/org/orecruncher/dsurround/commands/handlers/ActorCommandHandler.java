// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
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

    public static Component summon(ActorKind kind) {
        var handler = ContainerManager.resolve(WorldActorHandler.class);
        var summoned = GameUtils.getPlayer().map(player -> handler.summon(player, kind)).orElse(false);
        return Component.translatable(KEY + (summoned ? "summon.success" : "summon.off"), kind.getName());
    }
}
