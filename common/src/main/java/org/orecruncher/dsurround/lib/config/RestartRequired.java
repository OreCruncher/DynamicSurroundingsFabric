package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Changing the value of this property will require a restart for it to have an effect: of Minecraft if
 * {@code client} is true, otherwise leaving and rejoining the world.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RestartRequired {
    boolean client() default true;
}
