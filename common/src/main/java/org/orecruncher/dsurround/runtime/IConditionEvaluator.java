// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime;

import org.orecruncher.dsurround.lib.scripting.Script;

public interface IConditionEvaluator {
    boolean check(final Script conditions);

    Object eval(final Script conditions);
}
