package org.orecruncher.dsurround.lib.seasons.compat;

import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public abstract class AbstractSeasonProvider implements ISeasonalInformation {

    private final String providerName;

    protected AbstractSeasonProvider(String providerName) {
        this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
        return this.providerName;
    }
}
