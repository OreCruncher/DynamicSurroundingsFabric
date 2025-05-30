package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.Constants;

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

    // Common traits
    AQUATIC("aquatic"),
    AQUATIC_ICY("aquatic_icy"),
    BADLANDS("badlands"),
    BEACH("beach"),
    BIRCH_FOREST("birch_forest"),
    CAVE("cave"),
    COLD("cold"),
    DARK_FOREST("dark_forest"),
    DEAD("dead"),
    DEEP_OCEAN("deep_ocean"),
    DENSE_VEGETATION("dense_vegetation"),
    DESERT("desert"),
    DRY("dry"),
    END("end"),
    FLORAL("floral"),
    FLOWER_FOREST("flower_forest"),
    FOREST("forest"),
    HILL("hill"),
    HOT("hot"),
    ICY("icy"),
    JUNGLE("jungle"),
    LUSH("lush"),
    MAGICAL("magical"),
    MOUNTAIN("mountain"),
    MUSHROOM("mushroom"),
    NETHER("nether"),
    NETHER_FOREST("nether_forest"),
    OCEAN("ocean"),
    OLD_GROWTH("old_growth"),
    OUTER_END_ISLAND("outer_end_island"),
    OVERWORLD("overworld"),
    PLAINS("plains"),
    PLATEAU("plateau"),
    RARE("rare"),
    RIVER("river"),
    SANDY("sandy"),
    SAVANNA("savanna"),
    SHALLOW_OCEAN("shallow_ocean"),
    SNOWY("snowy"),
    SNOWY_PLAINS("snowy_plains"),
    SPARSE_VEGETATION("sparse_vegetation"),
    SPOOKY("spooky"),
    STONY_SHORES("stony_shores"),
    SWAMP("swamp"),
    TAIGA("taiga"),
    TEMPERATE("temperate"),
    UNDERGROUND("underground"),
    VOID("void"),
    WASTELAND("wasteland"),
    WET("wet"),
    WINDSWEPT("windswept"),

    // Additional traits
    CONIFEROUS("coniferous"),
    DECIDUOUS("deciduous");

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
    private final TagKey<Biome> biomeTag;

    BiomeTrait(String name) {
        this.name = name.toUpperCase();
        this.biomeTag = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "is_" + name.toLowerCase()));
    }

    public static BiomeTrait of(String name) {
        var result = mapper.get(name.toLowerCase());
        return result == null ? UNKNOWN : result;
    }

    private static void register(BiomeTrait trait) {
        mapper.put(trait.name, trait);
    }

    public String getName() {
        return this.name;
    }

    public TagKey<Biome> getBiomeTag() {
        return this.biomeTag;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
