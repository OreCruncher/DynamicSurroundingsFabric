package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.config.DimensionInfo;

public interface IDimensionLibrary extends ILibrary {
    DimensionInfo getData(final Level world);
}
