package org.orecruncher.dsurround.lib.di;

import java.lang.annotation.*;

/**
 * Used to mark a class as cacheable in a simple DI container.  A cached object will need to handle any
 * concurrency concerns.  Additionally, cached objects will have the lifespan of the container it is
 * within.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface Cacheable {
}