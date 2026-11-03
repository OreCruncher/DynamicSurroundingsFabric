// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries;

import java.util.stream.Stream;

public interface IDebug {

    Stream<String> dump();
}
