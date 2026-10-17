package org.orecruncher.dsurround.lib.config;

import org.orecruncher.dsurround.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the root of language translation keys
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TranslationRoot {
    String value() default Constants.MOD_ID;
}
