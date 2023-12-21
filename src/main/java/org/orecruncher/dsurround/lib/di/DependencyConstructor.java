package org.orecruncher.dsurround.lib.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark the constructor in a class that will be used for creation when a
 * dependency constructor resolves the class.  This constructor is needed when a target
 * class has more than one constructor that can be utilized.  If a class has a single
 * constructor the annotation is not needed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR})
public @interface DependencyConstructor {
}
