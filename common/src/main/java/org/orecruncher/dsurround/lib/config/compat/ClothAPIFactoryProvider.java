package org.orecruncher.dsurround.lib.config.compat;

import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.platform.KnownMod;

import java.util.Optional;

public class ClothAPIFactoryProvider implements IConfigScreenFactoryProvider {

    @Override
    public Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass) {
        if (KnownMod.CLOTH_CONFIG.isInstalled())
            return Optional.of(new ClothAPIFactory(ConfigurationData.getConfig(configClass)));
        return Optional.empty();
    }
}
