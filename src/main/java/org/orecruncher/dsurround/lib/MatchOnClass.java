package org.orecruncher.dsurround.lib;

public class MatchOnClass<T> implements IMatcher<T> {

    private final Class<?> clazz;

    public MatchOnClass(Class<?> clazz) {
        this.clazz = clazz;
    }

    public boolean match(T object) {
        return this.clazz.isInstance(object.getClass());
    }

}
