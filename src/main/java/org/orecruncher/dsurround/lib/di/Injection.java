package org.orecruncher.dsurround.lib.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field within a class as injectable.  The container instance will initialize
 * the class instance with appropriate values from the container.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Injection {
}
