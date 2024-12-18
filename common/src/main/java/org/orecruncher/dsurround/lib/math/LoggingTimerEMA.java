package org.orecruncher.dsurround.lib.math;

public class LoggingTimerEMA extends TimerEMA {

    private long timeMark;
    private long lastSample;

    public LoggingTimerEMA(final String name) {
        super(name);
    }

    public LoggingTimerEMA(final String name, final int periods) {
        super(name, periods);
    }

    public void begin() {
        this.timeMark = System.nanoTime();
    }

    public void end() {
        this.lastSample = System.nanoTime() - this.timeMark;
        this.update(this.lastSample);
    }

    public long getLastSample() {
        return this.lastSample;
    }

    public long getLastSampleMSecs() {
        return this.lastSample / 1000000;
    }
}