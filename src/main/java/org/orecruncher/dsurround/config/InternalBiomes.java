package org.orecruncher.dsurround.config;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;

import java.util.HashMap;
import java.util.Map;

public enum InternalBiomes {
    // Used to represent a value that is not the others.  Will not show up in
    // the lookup map.
    NONE("none"),
    UNDERGROUND("underground"),
    PLAYER("player"),
    VILLAGE("village"),
    CLOUDS("clouds"),
    SPACE("space"),
    UNDER_WATER("under_water"),
    UNDER_RIVER("under_river"),
    UNDER_OCEAN("under_ocean"),
    UNDER_DEEP_OCEAN("under_deep_ocean");

    private static final Map<String, InternalBiomes> lookup = new HashMap<>();

    static {
        for (var e : InternalBiomes.values())
            if (e != NONE)
                lookup.put(e.getName(), e);
    }

    private final String name;
    private final Identifier id;

    InternalBiomes(String name) {
        this.name = name;
        this.id = new Identifier(Client.ModId, String.format("fakebiome/%s", name));
    }

    @Nullable
    public static InternalBiomes getByName(String name) {
        return lookup.get(name.toLowerCase());
    }

    public String getName() {
        return this.name;
    }

    public Identifier getId() {
        return this.id;
    }
}
