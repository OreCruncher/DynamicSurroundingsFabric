package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a property used to be in the config file, as dotted paths from its top: "group.property". When a file is
 * loaded that has nothing at the property's place, the first of these with a value gives it, so moving a
 * property (to another group, or under another name) keeps what the player had set. List the most recent place
 * first; a property moved more than once lists each place it has been. The file is then written with the
 * property in its new place only.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MovedFrom {
    String[] value();

    /**
     * For a property whose values changed along with its place, how an old value becomes a new one: "old=new" pairs,
     * matched against the old value as written ("false=NONE"). An old value with no pair isn't carried over, and the
     * property keeps its default. Empty (the usual) carries the old value over as it is.
     */
    String[] values() default {};
}
