// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.seasons;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.KnownMod;
import org.orecruncher.dsurround.lib.seasons.compat.EclipticSeasons;
import org.orecruncher.dsurround.lib.seasons.compat.SereneSeasons;
import org.orecruncher.dsurround.lib.seasons.compat.VanillaSeasons;

public class SeasonManager {

    public static final ISeasonalInformation HANDLER;

    static {
        // Serene Seasons first, should both be installed
        if (KnownMod.SERENE_SEASONS.isInstalled()) {
            HANDLER = ContainerManager.resolve(SereneSeasons.class);
        } else if (KnownMod.ECLIPTIC_SEASONS.isInstalled()) {
            HANDLER = ContainerManager.resolve(EclipticSeasons.class);
        } else {
            HANDLER = ContainerManager.resolve(VanillaSeasons.class);
        }

        Library.LOGGER.info("Season provider: %s", HANDLER.getProviderName());
        checkDevelopmentRun(HANDLER.getProviderName());
    }

    /**
     * A development run says which seasons mod its build put in (-Ddsurround.seasonsMod: serene, ecliptic or none;
     * seasonsModOf in build.gradle). A different provider means the run's classpath is left over from an earlier
     * choice (the IDE's, before a Gradle reload), so it would test the wrong one: it stops here instead. By name, so
     * that a provider for a mod that isn't installed is never loaded.
     */
    static void checkDevelopmentRun(String providerName) {
        var expected = System.getProperty("dsurround.seasonsMod");
        if (expected == null)
            return;
        var actual = switch (providerName) {
            case "Serene Seasons" -> "serene";
            case "Ecliptic Seasons" -> "ecliptic";
            default -> "none";
        };
        if (!expected.equals(actual))
            throw new IllegalStateException("This development run was built to play with seasons mod '%s', but the season provider is %s: reload Gradle (the IDE's classpath is from an earlier choice)"
                    .formatted(expected, providerName));
    }
}
