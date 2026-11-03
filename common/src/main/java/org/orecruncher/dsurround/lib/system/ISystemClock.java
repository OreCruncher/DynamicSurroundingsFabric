// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.system;

import java.time.Instant;

public interface ISystemClock {
    long getUtcNanosNow();

    Instant getUtcNow();

    IStopwatch getStopwatch();
}
