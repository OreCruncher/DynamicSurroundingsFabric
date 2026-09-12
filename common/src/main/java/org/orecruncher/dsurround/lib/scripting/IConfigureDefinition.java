package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.NotNull;

public interface IConfigureDefinition {

    void defineFunction(String name, int arity, IScriptFunction function);
    void defineVariable(String name,  @NotNull IScriptVariable variable);
}
