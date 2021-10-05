package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeHillsMountainsAnalyzer implements IBiomeTraitAnalyzer {

    private static final String HILLS = "hills";
    private static final String MOUNTAINS = "mountains";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        if (id.getPath().contains(HILLS))
            return HILLS;

        if (id.getPath().contains(MOUNTAINS))
            return MOUNTAINS;

        return null;
    }
}
