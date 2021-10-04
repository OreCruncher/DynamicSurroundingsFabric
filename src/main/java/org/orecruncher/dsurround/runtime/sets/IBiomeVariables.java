package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IBiomeVariables {

    String getName();

    String getModId();

    String getId();

    float getRainfall();

    float getTemperature();

    String getCategory();

    String getRainType();
}