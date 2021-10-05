package org.orecruncher.dsurround.lib.biome;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.biometraits.*;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

public final class BiomeUtils {

    public static final Identifier PLAINS_ID = new Identifier("minecraft:plains");

    public static Biome getPlayerBiome(PlayerEntity player) {
        World world = player.getEntityWorld();
        return world.getBiomeAccess().getBiome(player.getBlockPos());
    }
}
