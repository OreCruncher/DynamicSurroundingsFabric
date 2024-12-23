package org.orecruncher.dsurround.lib.system;

import java.time.Instant;

public interface ISystemClock {
    long getUtcNanosNow();

    Instant getUtcNow();

    IStopwatch getStopwatch();
}
