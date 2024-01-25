package org.orecruncher.dsurround.lib.seasons;

import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.seasons.compat.SereneSeasons;
import org.orecruncher.dsurround.lib.seasons.compat.VanillaSeasons;

public class SeasonManager {

    public static final ISeasonalInformation HANDLER;

    static {
        if (Library.PLATFORM.isModLoaded(Constants.SERENE_SEASONS)) {
            HANDLER = new SereneSeasons();
        } else {
            HANDLER = new VanillaSeasons();
        }

        Library.LOGGER.info("Season provider: %s", HANDLER.getProviderName());
    }
}
