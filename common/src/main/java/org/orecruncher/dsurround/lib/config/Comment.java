package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comment associated with a property, if any. This is used if a translation is not available. It is also
 * written above the property in the config file.
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Comment {
    String value();

    /**
     * On a type: whether properties of this type that have no comment of their own use this one in the config
     * file. Ignored on fields.
     */
    boolean inherit() default false;
}
