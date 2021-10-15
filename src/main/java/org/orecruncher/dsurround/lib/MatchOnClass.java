package org.orecruncher.dsurround.lib;

public class MatchOnClass<T> implements IMatcher<T> {

    private final Class<?> clazz;

    public MatchOnClass(Class<?> clazz) {
        this.clazz = clazz;
    }

    public boolean match(T object) {
        return this.clazz.isInstance(object);
    }

    public static <T> MatchOnClass<T> parse(String classPaths) {
        String[] paths = classPaths.split("\\|");

        for (var p : paths) {
            try {
                var clazz = Class.forName(p);
                return new MatchOnClass<>(clazz);
            } catch(Throwable ignore) {
            }
        }
        return null;
    }
}
