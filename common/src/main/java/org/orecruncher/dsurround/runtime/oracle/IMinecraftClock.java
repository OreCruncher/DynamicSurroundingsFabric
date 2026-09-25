package org.orecruncher.dsurround.runtime.oracle;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.DayCycle;

public interface IMinecraftClock {

    int getDay();

    int getHour();

    int getMinute();

    boolean isAM();

    DayCycle getCycle();

    String getTimeOfDay();

    Component getFormattedTime();
}