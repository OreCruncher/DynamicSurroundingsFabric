package org.orecruncher.dsurround.lib.config;

import java.util.Optional;

public interface IConfigScreenFactoryProvider {
    Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass);
}
