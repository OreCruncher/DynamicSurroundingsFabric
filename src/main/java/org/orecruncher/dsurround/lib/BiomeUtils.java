package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

public final class BiomeUtils {

    private static final Identifier PLAINS_ID = new Identifier("minecraft:plains");

    @Environment(EnvType.CLIENT)
    public static Identifier getBiomeId(Biome biome) {
        Registry<Biome> biomeRegistry = GameUtils.getRegistryManager().get(Registry.BIOME_KEY);
        Identifier id = biomeRegistry.getId(biome);
        if (id == null)
            id = PLAINS_ID;
        return id;
    }

    @Environment(EnvType.CLIENT)
    public static String getBiomeName(Biome biome) {
        Identifier id = getBiomeId(biome);
        final String fmt = String.format("biome.%s.%s", id.getNamespace(), id.getPath());
        return I18n.translate(fmt);
    }
}
