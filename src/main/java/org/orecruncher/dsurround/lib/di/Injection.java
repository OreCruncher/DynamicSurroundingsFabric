package org.orecruncher.dsurround.lib.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field within a class as injectable.  The container instance will initialize
 * the class instance with appropriate values from the container. Note that injectors are
 *  processed AFTER the constructor has completed and before the instance is handed back to
 * the caller.  Constructors should not take a dependency on injected fields.  If a
 * constructor needs the dependency, inject using constructor parameters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Injection {
}
