package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public abstract class SliderControl extends AbstractSliderButton {

    protected final double step;
    protected final double min;
    protected final double max;

    public SliderControl(int x, int y, int width, int height, double minValue, double maxValue, float valueStep, double currentValue) {
        super(x, y, width, height, Component.empty(), getRatio(currentValue, minValue, maxValue, valueStep));
        this.min = minValue;
        this.max = maxValue;
        this.step = valueStep;
    }

    private static double getRatio(double value, double min, double max, double step) {
        double adjusted = adjust(value, min, max, step);
        return Mth.clamp((adjusted - min) / (max - min), 0.0D, 1.0D);
    }

    private static double getValue(double ratio, double min, double max, double step) {
        double value = Mth.clamp(ratio, 0.0D, 1.0D);
        return adjust(Mth.lerp(value, min, max), min, max, step);
    }

    private static double adjust(double value, double min, double max, double step) {
        if (step > 0) {
            value = (step * (float) Math.round(value / step));
        }
        return Mth.clamp(value, min, max);
    }

    public double getValue() {
        return SliderControl.getValue(this.value, this.min, this.max, this.step);
    }

    @Override
    protected abstract void updateMessage();

    @Override
    protected abstract void applyValue();
}