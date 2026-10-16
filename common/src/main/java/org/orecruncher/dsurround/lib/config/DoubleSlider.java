package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Value range of a double property, edited with a slider in the GUI that moves in steps of {@code step}. As with
 * {@link Slider}, this is the property's range, so a field can't also have {@link DoubleRange}.
 * <p>
 * The step must divide the range evenly, have at most six decimal places, and the default must be one of the
 * positions. The slider shows as many decimal places as the step has (or the minimum, if it has more). Values
 * in the file don't have to be on a step; the slider shows the nearest one.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DoubleSlider {
    double min();

    double max();

    double step();
}
