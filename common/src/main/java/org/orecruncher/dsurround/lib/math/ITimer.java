// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.math;

/**
 * A timing statistic shown in diagnostics. Its toString() gives the name and value for display.
 */
public interface ITimer {
    double getMSecs();
}
