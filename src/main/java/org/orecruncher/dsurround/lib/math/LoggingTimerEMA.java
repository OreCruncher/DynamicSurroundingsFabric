package org.orecruncher.dsurround.lib.math;

@SuppressWarnings("unused")
public class LoggingTimerEMA extends TimerEMA {

    private long timeMark;

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
        this.update(System.nanoTime() - this.timeMark);
    }
}