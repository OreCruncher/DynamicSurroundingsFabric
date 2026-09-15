package org.orecruncher.dsurround.lib.seasons.compat;

import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public abstract class AbstractSeasonProvider implements ISeasonalInformation {

    protected static final IBiomeLibrary BIOME_LIBRARY = ContainerManager.resolve(IBiomeLibrary.class);

    private final String providerName;

    protected AbstractSeasonProvider(String providerName) {
        this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
        return this.providerName;
    }
}
