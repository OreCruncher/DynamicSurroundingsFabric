package org.orecruncher.dsurround.lib.scripting;

import java.util.List;

@FunctionalInterface
public interface IScriptFunction {
    Object evaluate(List<Object> arguments);
}
