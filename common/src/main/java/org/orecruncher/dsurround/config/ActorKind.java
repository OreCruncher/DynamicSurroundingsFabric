// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;

import java.util.Arrays;
import java.util.Locale;

/**
 * The kinds of world actor: things that move through the world on their own, seen by everyone nearby, such as dust
 * devils. A biome's {@code actors} list in {@code biomes.json} names them by id ({@code dsurround:dust_devil}).
 */
public enum ActorKind {
    DUST_DEVIL("dust_devil"),
    WATERSPOUT("waterspout"),
    SNOWSPOUT("snowspout");

    private final Identifier id;

    ActorKind(String name) {
        this.id = Constants.asId(name);
    }

    public Identifier getId() {
        return this.id;
    }

    /**
     * Its name in commands
     */
    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ActorKind byId(Identifier id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(null);
    }
}
