package org.orecruncher.dsurround.lib.scripting;

@FunctionalInterface
public interface IScriptFunction {
    Object evaluate(Object[] arguments);
}
