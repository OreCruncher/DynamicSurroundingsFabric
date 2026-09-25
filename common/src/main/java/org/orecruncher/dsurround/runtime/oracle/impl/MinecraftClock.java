package org.orecruncher.dsurround.runtime.oracle.impl;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.ITickable;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.oracle.IMinecraftClock;

import java.text.DecimalFormat;

public final class MinecraftClock implements IMinecraftClock, ITickable {

    private static final String AM = "dsurround.format.AM";
    private static final String PM = "dsurround.format.PM";
    private static final String TIME_FORMAT = "dsurround.format.TimeOfDay";
    private static final DecimalFormat MINUTE_FORMAT = new DecimalFormat("00");

    private final ILevelOracle levelOracle;

    private int day;
    private int hour;
    private int minute;
    private boolean isAM;
    private DayCycle cycle = DayCycle.DAYTIME;

    public MinecraftClock(ILevelOracle levelOracle) {
        this.levelOracle = levelOracle;
        ClientState.CLIENT_TICK_START_EVENT.register(_ -> this.tick());
    }

    public void tick() {
        var time = this.levelOracle.worldTime();
        this.day = (int) (time / 24000);
        time -= this.day * 24000L;
        this.day++; // It's day 1, not 0 :)
        this.hour = (int) (time / 1000);
        time -= this.hour * 1000L;
        this.minute = (int) (time / 16.666D);

        this.hour += 6;
        if (this.hour >= 24) {
            this.hour -= 24;
            this.day++;
        }

        this.isAM = this.hour < 12;

        this.cycle = this.levelOracle.currentDiurnalState();
    }

    @Override
    public int getDay() {
        return this.day;
    }

    @Override
    public int getHour() {
        return this.hour;
    }

    @Override
    public int getMinute() {
        return this.minute;
    }

    @Override
    public boolean isAM() {
        return this.isAM;
    }

    @Override
    public DayCycle getCycle() {
        return this.cycle;
    }

    @Override
    public String getTimeOfDay() {
        return this.cycle.getFormattedName();
    }

    @Override
    public Component getFormattedTime() {
        int h = this.hour;
        if (h > 12)
            h -= 12;
        if (h == 0)
            h = 12;

        return Component.translatable(TIME_FORMAT, this.day, h, MINUTE_FORMAT.format(this.minute), Component.translatable(this.isAM ? AM : PM), this.getTimeOfDay());
    }

    @Override
    public String toString() {
        return '[' + getFormattedTime().getVisualOrderText().toString() + ']';
    }
}