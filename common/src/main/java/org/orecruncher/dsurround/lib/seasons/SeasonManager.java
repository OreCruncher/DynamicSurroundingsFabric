package org.orecruncher.dsurround.lib.seasons;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.KnownMod;
import org.orecruncher.dsurround.lib.seasons.compat.SereneSeasons;
import org.orecruncher.dsurround.lib.seasons.compat.VanillaSeasons;

public class SeasonManager {

    public static final ISeasonalInformation HANDLER;

    static {
        if (KnownMod.SERENE_SEASONS.isInstalled()) {
            HANDLER = ContainerManager.resolve(SereneSeasons.class);
        } else {
            HANDLER = ContainerManager.resolve(VanillaSeasons.class);
        }

        Library.LOGGER.info("Season provider: %s", HANDLER.getProviderName());
    }
}
