package org.orecruncher.dsurround.lib.reflection;

@FunctionalInterface
public interface IMethodCallHandler {
    Object invoke(Object target, Object... args);
}
