package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class BiomeTrait {

    // Generic case of WTF
    public static final BiomeTrait UNKNOWN = new BiomeTrait("unknown");

    /* From Biome categories */
    public static final BiomeTrait NONE = new BiomeTrait(Biome.Category.NONE.getName());
    public static final BiomeTrait TAIGA = new BiomeTrait(Biome.Category.TAIGA.getName());
    public static final BiomeTrait EXTREME_HILLS = new BiomeTrait(Biome.Category.EXTREME_HILLS.getName());
    public static final BiomeTrait JUNGLE = new BiomeTrait(Biome.Category.JUNGLE.getName());
    public static final BiomeTrait MESA = new BiomeTrait(Biome.Category.MESA.getName());
    public static final BiomeTrait PLAINS = new BiomeTrait(Biome.Category.PLAINS.getName());
    public static final BiomeTrait SAVANNA = new BiomeTrait(Biome.Category.SAVANNA.getName());
    public static final BiomeTrait ICY = new BiomeTrait(Biome.Category.ICY.getName());
    public static final BiomeTrait THEEND = new BiomeTrait(Biome.Category.THEEND.getName());
    public static final BiomeTrait BEACH = new BiomeTrait(Biome.Category.BEACH.getName());
    public static final BiomeTrait FOREST = new BiomeTrait(Biome.Category.FOREST.getName());
    public static final BiomeTrait OCEAN = new BiomeTrait(Biome.Category.OCEAN.getName());
    public static final BiomeTrait DESERT = new BiomeTrait(Biome.Category.DESERT.getName());
    public static final BiomeTrait RIVER = new BiomeTrait(Biome.Category.RIVER.getName());
    public static final BiomeTrait SWAMP = new BiomeTrait(Biome.Category.SWAMP.getName());
    public static final BiomeTrait MUSHROOM = new BiomeTrait(Biome.Category.MUSHROOM.getName());
    public static final BiomeTrait NETHER = new BiomeTrait(Biome.Category.NETHER.getName());
    public static final BiomeTrait UNDERGROUND = new BiomeTrait(Biome.Category.UNDERGROUND.getName());

    /* Extended categories */
    public static final BiomeTrait WATER = new BiomeTrait("WATER");

    public static final BiomeTrait WET = new BiomeTrait("WET");
    public static final BiomeTrait DRY = new BiomeTrait("DRY");

    public static final BiomeTrait HOT = new BiomeTrait("HOT");
    public static final BiomeTrait COLD = new BiomeTrait("COLD");

    public static final BiomeTrait SPARSE = new BiomeTrait("SPARSE");
    public static final BiomeTrait DENSE = new BiomeTrait("DENSE");

    public static final BiomeTrait CONIFEROUS = new BiomeTrait("CONIFEROUS");

    public static final BiomeTrait SPOOKY = new BiomeTrait("SPOOKY");
    public static final BiomeTrait DEAD = new BiomeTrait("DEAD");
    public static final BiomeTrait MAGICAL = new BiomeTrait("MAGICAL");
    public static final BiomeTrait PLATEAU = new BiomeTrait("PLATEAU");

    public static final BiomeTrait MOUNTAIN = new BiomeTrait("MOUNTAIN");
    public static final BiomeTrait HILLS = new BiomeTrait("HILLS");
    public static final BiomeTrait SANDY = new BiomeTrait("SANDY");
    public static final BiomeTrait SNOWY = new BiomeTrait("SNOWY");
    public static final BiomeTrait WASTELAND = new BiomeTrait("WASTELAND");
    public static final BiomeTrait VOID = new BiomeTrait("VOID");

    public static final BiomeTrait OVERWORLD = new BiomeTrait("OVERWORLD");

    public static final BiomeTrait DEEP = new BiomeTrait("DEEP");

    private static final Map<String, BiomeTrait> mapper = new HashMap<>();

    private final String name;

    BiomeTrait(String name) {
        this.name = name.toUpperCase();
        assert !mapper.containsKey(this.name);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BiomeTrait && this.name.equals(((BiomeTrait) obj).name);
    }

    public static BiomeTrait of(Biome.Category category) {
        var result =  mapper.get(category.getName().toUpperCase());
        return result == null ? UNKNOWN : result;
    }

    public static @Nullable BiomeTrait of(String name) {
        var result = mapper.get(name.toUpperCase());
        return result == null ? UNKNOWN : result;
    }

    private static void register(BiomeTrait trait) {
        mapper.put(trait.name, trait);
    }

    static {
        register(BiomeTrait.NONE);  // stone_shore why?
        register(BiomeTrait.TAIGA);
        register(BiomeTrait.EXTREME_HILLS);
        register(BiomeTrait.JUNGLE);
        register(BiomeTrait.MESA);
        register(BiomeTrait.PLAINS);
        register(BiomeTrait.SAVANNA);
        register(BiomeTrait.ICY);
        register(BiomeTrait.THEEND);
        register(BiomeTrait.BEACH);
        register(BiomeTrait.FOREST);
        register(BiomeTrait.OCEAN);
        register(BiomeTrait.DESERT);
        register(BiomeTrait.RIVER);
        register(BiomeTrait.SWAMP);
        register(BiomeTrait.MUSHROOM);
        register(BiomeTrait.NETHER);
        register(BiomeTrait.UNDERGROUND);
        register(BiomeTrait.WATER);
        register(BiomeTrait.WET);
        register(BiomeTrait.DRY);
        register(BiomeTrait.HOT);
        register(BiomeTrait.COLD);
        register(BiomeTrait.SPARSE);
        register(BiomeTrait.DENSE);
        register(BiomeTrait.CONIFEROUS);
        register(BiomeTrait.SPOOKY);
        register(BiomeTrait.DEAD);
        register(BiomeTrait.MAGICAL);
        register(BiomeTrait.PLATEAU);
        register(BiomeTrait.MOUNTAIN);
        register(BiomeTrait.HILLS);
        register(BiomeTrait.SANDY);
        register(BiomeTrait.SNOWY);
        register(BiomeTrait.WASTELAND);
        register(BiomeTrait.VOID);
        register(BiomeTrait.OVERWORLD);
        register(BiomeTrait.DEEP);
    }
}
