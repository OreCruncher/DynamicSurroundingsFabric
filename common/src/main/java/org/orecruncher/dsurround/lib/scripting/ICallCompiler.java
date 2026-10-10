package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Call;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;

/**
 * Lets a function compile its calls to a specialized expression node instead of the standard call, for example a
 * conditional that evaluates only the selected branch without allocating anything. Set with
 * {@link FunctionBuilder#compileWith(ICallCompiler)}.
 */
@FunctionalInterface
public interface ICallCompiler {

    /**
     * @param call The standard call, with constant arguments already converted
     * @return The node to use in place of the call, or null to use the call itself
     */
    @Nullable Expression compile(Call call);
}
