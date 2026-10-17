package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the field is a property. Supported types are boolean, int, double, String and enums (primitive or
 * boxed), and nested classes of properties, which become property groups.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Property {
    /**
     * The key segment for formulating a lookup key to generate language resource ids.
     */
    String value() default "";
}
