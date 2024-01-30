package org.orecruncher.dsurround.lib.seasons.compat;

import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public abstract class AbstractSeasonProvider implements ISeasonalInformation {

    private final String providerName;

    protected AbstractSeasonProvider(String providerName) {
        this.providerName = providerName;
        AssetLibraryEvent.RELOAD.register(this::reloadResources);
    }

    @Override
    public String getProviderName() {
        return this.providerName;
    }

    protected abstract void reloadResources(IReloadEvent.Scope scope);
}
