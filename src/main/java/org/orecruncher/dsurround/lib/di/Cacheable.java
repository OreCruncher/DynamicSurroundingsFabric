package org.orecruncher.dsurround.lib.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark a class as cacheable in a simple DI container.  A cached object will need to handle any
 * concurrency concerns.  Additionally, cached objects will have the lifespan of the container it is
 * within.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Cacheable {
}