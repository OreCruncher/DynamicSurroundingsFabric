// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.config;

import java.util.Optional;

public interface IConfigScreenFactoryProvider {
    Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass);
}
