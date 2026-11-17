// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime;

import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions;

import java.util.function.Consumer;

public interface IConditionEvaluator {
    boolean check(final Script conditions);

    Object eval(final Script conditions);

    /**
     * Takes the config files' script definitions (constants, and the functions for conditions), in place of the last;
     * each problem with them is described to {@code problems}. Stand-ins have none.
     */
    default void defineScripts(ScriptDefinitions definitions, Consumer<String> problems) {
    }
}
