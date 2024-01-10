package org.orecruncher.dsurround.lib.config.factories;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.platform.Services;

/**
 * Determines which config system is installed and provides back the necessary configuration
 * screen implementation.
 */
public class FactoryResolver {

    private FactoryResolver() {

    }

    @Nullable
    public static IScreenFactory getModConfigScreenFactory() {
        if (Services.PLATFORM.isModLoaded(Constants.CLOTH_CONFIG)) {
            return ClothAPIFactory::createDefaultConfigScreen;
        }

        if (Services.PLATFORM.isModLoaded(Constants.YACL)) {
            return YaclFactory::createDefaultConfigScreen;
        }

        return null;
    }
}
