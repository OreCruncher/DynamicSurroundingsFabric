package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;

public interface IVariableAccess {

    void put(String variableName, @Nullable Object value);
}
