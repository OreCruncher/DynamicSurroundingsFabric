package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum BiomeTrait {
    // Generic case of WTF
    UNKNOWN("UNKNOWN"),
    // Special internal traits for synthetic biomes
    SYNTHETIC("SYNTHETIC"),
    INSIDE("inside"),
    VILLAGE("VILLAGE"),
    PLAYER("PLAYER"),
    SPACE("SPACE"),
    CLOUDS("CLOUDS"),
    UNDER_RIVER("UNDER_RIVER"),
    UNDER_WATER("UNDER_WATER"),
    UNDER_OCEAN("UNDER_OCEAN"),
    // Biome categories as traits
    NONE("none"),
    TAIGA("taiga"),
    EXTREME_HILLS("extreme_hills"),
    JUNGLE("jungle"),
    MESA("mesa"),
    PLAINS("plains"),
    SAVANNA("savanna"),
    ICY("icy"),
    THEEND("the_end"),
    BEACH("beach"),
    FOREST("forest"),
    OCEAN("ocean"),
    DESERT("desert"),
    RIVER("river"),
    SWAMP("swamp"),
    MUSHROOM("mushroom"),
    NETHER("nether"),
    UNDERGROUND("underground"),
    /* Extended categories */
    WATER("WATER"),
    WET("WET"),
    DRY("DRY"),
    HOT("HOT"),
    COLD("COLD"),
    TEMPERATE("TEMPERATE"),
    SPARSE("SPARSE"),
    DENSE("DENSE"),
    CONIFEROUS("CONIFEROUS"),
    DECIDUOUS("DECIDUOUS"),
    SPOOKY("SPOOKY"),
    DEAD("DEAD"),
    MAGICAL("MAGICAL"),
    PLATEAU("PLATEAU"),
    MOUNTAIN("MOUNTAIN"),
    SANDY("SANDY"),
    SNOWY("SNOWY"),
    WASTELAND("WASTELAND"),
    VOID("VOID"),
    OVERWORLD("OVERWORLD"),
    DEEP("DEEP"),
    WINDSWEPT("WINDSWEPT"),
    FLORAL("FLORAL"),
    BADLANDS("BADLANDS"),
    CAVES("CAVES"),
    RARE("RARE"),
    LUSH("LUSH");

    private static final Map<String, BiomeTrait> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(BiomeTrait::getName, (category) -> category));
    public static final Codec<BiomeTrait> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown biome trait"), d -> d.name);

    private static final Map<String, BiomeTrait> mapper = new HashMap<>();

    static {

        for(var trait : values()) {
            if (trait == UNKNOWN)
                continue;
            register(trait);
        }
    }

    private final String name;

    BiomeTrait(String name) {
        this.name = name.toUpperCase();
    }

    public static BiomeTrait of(String name) {
        var result = mapper.get(name.toUpperCase());
        return result == null ? UNKNOWN : result;
    }

    private static void register(BiomeTrait trait) {
        mapper.put(trait.name, trait);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
