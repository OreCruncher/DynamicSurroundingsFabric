package org.orecruncher.dsurround.lib.seasons;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.seasons.compat.VanillaSeasons;

public class SeasonManager {

    public static final ISeasonalInformation HANDLER;

    static {
        HANDLER = new VanillaSeasons();

        Library.LOGGER.info("Season provider: %s", HANDLER.getProviderName());
    }
}
