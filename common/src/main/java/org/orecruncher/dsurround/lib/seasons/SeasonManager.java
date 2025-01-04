package org.orecruncher.dsurround.lib.seasons;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.seasons.compat.SereneSeasons;
import org.orecruncher.dsurround.lib.seasons.compat.VanillaSeasons;

public class SeasonManager {

    public static final ISeasonalInformation HANDLER;

    static {
        if (Platform.isModLoaded(Constants.SERENE_SEASONS)) {
            HANDLER = ContainerManager.resolve(SereneSeasons.class);
        } else {
            HANDLER = ContainerManager.resolve(VanillaSeasons.class);
        }

        Library.LOGGER.info("Season provider: %s", HANDLER.getProviderName());
    }
}
