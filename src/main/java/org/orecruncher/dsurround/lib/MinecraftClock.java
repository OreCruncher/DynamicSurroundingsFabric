package org.orecruncher.dsurround.lib;

import net.minecraft.world.World;

import java.text.DecimalFormat;

public class MinecraftClock {

    private static final String AM = "dsurround.format.AM";
    private static final String PM = "dsurround.format.PM";
    private static final String TIME_FORMAT = "dsurround.format.TimeOfDay";

    private static final DecimalFormat minuteFormat = new DecimalFormat("00");

    protected int day;
    protected int hour;
    protected int minute;
    protected boolean isAM;
    protected DayCycle cycle = DayCycle.DAYTIME;

    public MinecraftClock() {

    }

    public MinecraftClock(final World world) {
        update(world);
    }

    public void update(final World world) {

        long time = world.getTimeOfDay();
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

        this.cycle = DayCycle.getCycle(world);
    }

    public int getDay() {
        return this.day;
    }

    public int getHour() {
        return this.hour;
    }

    public int getMinute() {
        return this.minute;
    }

    public boolean isAM() {
        return this.isAM;
    }

    public DayCycle getCycle() {
        return this.cycle;
    }

    public String getTimeOfDay() {
        return this.cycle.getFormattedName();
    }

    public String getFormattedTime() {
        int h = this.hour;
        if (h > 12)
            h -= 12;
        if (h == 0)
            h = 12;

        String format = Localization.load(TIME_FORMAT);
        String amPm = Localization.load(this.isAM ? AM : PM);
        return String.format(format, this.day, h, minuteFormat.format(this.minute), amPm, this.cycle.getFormattedName());
    }

    @Override
    public String toString() {
        return '[' + getFormattedTime() + ' ' + getTimeOfDay() + ']';
    }
}