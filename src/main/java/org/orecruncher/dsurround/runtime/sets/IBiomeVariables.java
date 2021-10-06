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

    String getPrecipitationType();

    String getTraits();

    boolean is(String trait);

    boolean isAll(String... trait);

    boolean isOneOf(String... trait);
}