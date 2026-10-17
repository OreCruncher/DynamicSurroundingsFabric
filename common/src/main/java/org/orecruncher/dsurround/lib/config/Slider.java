package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Value range of an integer property, edited with a slider in the GUI. A slider needs both limits, so neither
 * has a default. This is the property's range, as {@link IntegerRange} would be, so a field can't have both.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Slider {
    int min();

    int max();
}
