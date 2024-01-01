package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum InternalBiomes {
    // Used to represent a value that is not the others.  Will not show up in
    // the lookup map.
    NONE("none"),
    INSIDE("inside", BiomeTrait.INSIDE),
    UNDERGROUND("underground", BiomeTrait.UNDERGROUND),
    PLAYER("player", BiomeTrait.PLAYER),
    VILLAGE("village", BiomeTrait.VILLAGE),
    CLOUDS("clouds", BiomeTrait.CLOUDS),
    SPACE("space", BiomeTrait.SPACE),
    UNDER_WATER("under_water", BiomeTrait.UNDER_WATER),
    UNDER_RIVER("under_river", BiomeTrait.UNDER_RIVER),
    UNDER_OCEAN("under_ocean", BiomeTrait.UNDER_OCEAN),
    UNDER_DEEP_OCEAN("under_deep_ocean", BiomeTrait.UNDER_OCEAN, BiomeTrait.DEEP);

    private static final Map<String, InternalBiomes> lookup = new HashMap<>();

    static {
        for (var e : InternalBiomes.values())
            if (e != NONE)
                lookup.put(e.getName(), e);
    }

    private final String name;
    private final ResourceLocation id;
    private final BiomeTraits traits;

    InternalBiomes(String name, BiomeTrait... traits) {
        this.name = name;
        this.id = new ResourceLocation(Constants.MOD_ID, String.format("fakebiome/%s", name));
        traits = Arrays.copyOf(traits, traits.length + 1);
        traits[traits.length - 1] = BiomeTrait.FAKE;
        this.traits = BiomeTraits.from(traits);
    }

    @Nullable
    public static InternalBiomes getByName(String name) {
        return lookup.get(name.toLowerCase());
    }

    public String getName() {
        return this.name;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public BiomeTraits getTraits() {
        return this.traits;
    }
}
