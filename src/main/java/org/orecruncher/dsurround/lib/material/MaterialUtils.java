package org.orecruncher.dsurround.lib.material;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
//import net.minecraft.block.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class MaterialUtils {
/*
    private static final Reference2ObjectOpenHashMap<Material, String> materialMap = new Reference2ObjectOpenHashMap<>();
    private static final Map<String, Material> materialMapInv = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        materialMap.defaultReturnValue("CUSTOM");

        materialMap.put(Material.AIR, "AIR");
        materialMap.put(Material.STRUCTURE_VOID, "STRUCTURE_VOID");
        materialMap.put(Material.PORTAL, "PORTAL");
        materialMap.put(Material.CARPET, "CARPET");
        materialMap.put(Material.PLANT, "PLANT");
        materialMap.put(Material.UNDERWATER_PLANT, "UNDERWATER_PLANT");
        materialMap.put(Material.REPLACEABLE_PLANT, "REPLACEABLE_PLANT");
        materialMap.put(Material.NETHER_SHOOTS, "NETHER_SHOOTS");
        materialMap.put(Material.REPLACEABLE_UNDERWATER_PLANT, "REPLACEABLE_UNDERWATER_PLANT");
        materialMap.put(Material.WATER, "WATER");
        materialMap.put(Material.BUBBLE_COLUMN, "BUBBLE_COLUMN");
        materialMap.put(Material.LAVA, "LAVA");
        materialMap.put(Material.SNOW_LAYER, "SNOW_LAYER");
        materialMap.put(Material.FIRE, "FIRE");
        materialMap.put(Material.DECORATION, "DECORATION");
        materialMap.put(Material.COBWEB, "COBWEB");
        materialMap.put(Material.SCULK, "SCULK");
        materialMap.put(Material.REDSTONE_LAMP, "REDSTONE_LAMP");
        materialMap.put(Material.ORGANIC_PRODUCT, "ORGANIC_PRODUCT");
        materialMap.put(Material.SOIL, "SOIL");
        materialMap.put(Material.SOLID_ORGANIC, "SOLID_ORGANIC");
        materialMap.put(Material.DENSE_ICE, "DENSE_ICE");
        materialMap.put(Material.AGGREGATE, "AGGREGATE");
        materialMap.put(Material.SPONGE, "SPONGE");
        materialMap.put(Material.SHULKER_BOX, "SHULKER_BOX");
        materialMap.put(Material.WOOD, "WOOD");
        materialMap.put(Material.NETHER_WOOD, "NETHER_WOOD");
        materialMap.put(Material.BAMBOO_SAPLING, "BAMBOO_SAPLING");
        materialMap.put(Material.BAMBOO, "BAMBOO");
        materialMap.put(Material.WOOL, "WOOL");
        materialMap.put(Material.TNT, "TNT");
        materialMap.put(Material.LEAVES, "LEAVES");
        materialMap.put(Material.GLASS, "GLASS");
        materialMap.put(Material.ICE, "ICE");
        materialMap.put(Material.CACTUS, "CACTUS");
        materialMap.put(Material.STONE, "STONE");
        materialMap.put(Material.METAL, "METAL");
        materialMap.put(Material.SNOW_BLOCK, "SNOW_BLOCK");
        materialMap.put(Material.REPAIR_STATION, "REPAIR_STATION");
        materialMap.put(Material.BARRIER, "BARRIER");
        materialMap.put(Material.PISTON, "PISTON");
        materialMap.put(Material.MOSS_BLOCK, "MOSS_BLOCK");
        materialMap.put(Material.GOURD, "GOURD");
        materialMap.put(Material.EGG, "EGG");
        materialMap.put(Material.CAKE, "CAKE");
        materialMap.put(Material.AMETHYST, "AMETHYST");
        materialMap.put(Material.POWDER_SNOW, "POWDER_SNOW");

        // Create the inverse map
        for (final Map.Entry<Material, String> kvp : materialMap.entrySet()) {
            materialMapInv.put(kvp.getValue(), kvp.getKey());
        }
    }

    public static Set<Material> getMaterials() {
        return materialMap.keySet();
    }

    @Nullable
    public static Material getMaterial(final String name) {
        return materialMapInv.get(name);
    }

    public static String getMaterialName(final Material material) {
        return materialMap.get(material);
    }
    */
}