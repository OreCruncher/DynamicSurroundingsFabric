package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.orecruncher.dsurround.mixins.MixinClientWorldProperties;

public class WorldUtils {
    @Environment(EnvType.CLIENT)
    public static boolean isSuperFlat(final World world) {
        final WorldProperties info = world.getLevelProperties();
        return info instanceof MixinClientWorldProperties && ((MixinClientWorldProperties)info).isFlatWorld();
    }

    public static BlockPos getTopSolidOrLiquidBlock(final World world, final BlockPos pos) {
        return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos);
    }

    public static float getTemperatureAt(final World world, final BlockPos pos) {
        return world.getBiomeAccess().getBiome(pos).getTemperature(pos);
    }
}
