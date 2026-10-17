package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the option as being associated with an experimental feature. Tooltips and coloration are used to indicate
 * the implementation state. Experimental features are not guaranteed to make it to a release state, and may change.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Experimental {
}
