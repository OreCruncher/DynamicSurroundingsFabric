package org.orecruncher.dsurround.lib.config.compat;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.IScreenFactory;

import java.util.Optional;

public class ClothAPIFactoryProvider implements IConfigScreenFactoryProvider {

    @Override
    public Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass) {
        if (Platform.isModLoaded(Constants.CLOTH_CONFIG_FABRIC) || Platform.isModLoaded(Constants.CLOTH_CONFIG_NEOFORGE))
            return Optional.of(new ClothAPIFactory(ConfigurationData.getConfig(configClass)));
        return Optional.empty();
    }
}
