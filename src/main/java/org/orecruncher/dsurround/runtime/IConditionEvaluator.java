package org.orecruncher.dsurround.runtime;

import org.orecruncher.dsurround.lib.scripting.Script;

public interface IConditionEvaluator {
    boolean check(final Script conditions);

    Object eval(final Script conditions);
}
