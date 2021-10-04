package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IDimensionVariables {

    String getId();

    String getDimName();

    boolean hasSky();

    boolean isSuperFlat();
}