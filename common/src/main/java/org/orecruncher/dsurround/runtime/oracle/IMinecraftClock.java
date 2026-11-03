// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.oracle;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.time.DayCycle;

public interface IMinecraftClock {

    int getDay();

    int getHour();

    int getMinute();

    boolean isAM();

    DayCycle getCycle();

    String getTimeOfDay();

    Component getFormattedTime();
}