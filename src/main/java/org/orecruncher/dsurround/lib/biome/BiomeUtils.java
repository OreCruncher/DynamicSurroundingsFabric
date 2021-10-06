package org.orecruncher.dsurround.lib.biome;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.BuiltinBiomes;

public final class BiomeUtils {

    // Default to the VOID because PLAINS has data associated with it
    public static final Identifier DEFAULT_ID = BiomeKeys.THE_VOID.getValue();
    public static final Biome DEFAULT_BIOME = BuiltinBiomes.THE_VOID;

    public static Biome getPlayerBiome(PlayerEntity player) {
        World world = player.getEntityWorld();
        return world.getBiomeAccess().getBiome(player.getBlockPos());
    }
}
