package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Style elements to apply when rendering in the configuration display
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TextStyle {
    String color() default "";
    boolean italic() default false;
    boolean bold() default false;
    boolean underlined() default false;
}
