package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;

@Environment(EnvType.CLIENT)
public interface IDimensionLibrary extends ILibrary {
    DimensionInfo getData(final World world);
}
