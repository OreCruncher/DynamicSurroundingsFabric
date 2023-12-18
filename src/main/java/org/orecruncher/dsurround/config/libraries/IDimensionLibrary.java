package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.World;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;

public interface IDimensionLibrary extends ILibrary {
    DimensionInfo getData(final World world);
}
