package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeSpookyAnalyzer implements IBiomeTraitAnalyzer {

    private static final String SPOOKY = "spooky";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        if (id.getPath().contains("dark"))
            return SPOOKY;
        return null;
    }
}
