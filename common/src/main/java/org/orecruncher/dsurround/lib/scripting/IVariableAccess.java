package org.orecruncher.dsurround.lib.scripting;

@FunctionalInterface
public interface IVariableAccess {

    void put(String variableName, IScriptVariable value);
}
