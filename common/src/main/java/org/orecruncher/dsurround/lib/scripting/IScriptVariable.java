// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting;

public interface IScriptVariable {

    /**
     * Returns the script value at the time of execution.
     *
     * @return Value of the variable
     */
    Object getValue();
}
