package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a script function: its parameters, how many arguments it accepts, and how the engine may treat it.
 * Usually created with {@link IConfigureDefinition#function(String)}.
 *
 * @param name        Function name as used in scripts, such as "math.round"
 * @param parameters  Declared parameters; the first {@code minArgs} are required, the rest optional
 * @param minArgs     Minimum number of arguments
 * @param varArgType  Type of any further arguments, or null if no further arguments are accepted
 * @param pure        The result depends only on the arguments. Calls whose arguments are all constants are
 *                    evaluated once at compile time.
 * @param lazy        Arguments are evaluated only when the handler requests them
 * @param handler     Implementation
 * @param compiler    Optional specialized compilation of calls, or null for the standard call
 */
public record ScriptFunction(String name, List<ArgType<?>> parameters, int minArgs, @Nullable ArgType<?> varArgType,
                             boolean pure, boolean lazy, IScriptFunctionHandler handler, @Nullable ICallCompiler compiler) {

    public ScriptFunction(String name, List<ArgType<?>> parameters, int minArgs, @Nullable ArgType<?> varArgType,
                          boolean pure, boolean lazy, IScriptFunctionHandler handler) {
        this(name, parameters, minArgs, varArgType, pure, lazy, handler, null);
    }

    /**
     * @return Maximum number of arguments, or -1 if unlimited
     */
    public int maxArgs() {
        return this.varArgType == null ? this.parameters.size() : -1;
    }

    /**
     * @return Type of the argument at the index
     */
    public ArgType<?> parameterType(int index) {
        return index < this.parameters.size() ? this.parameters.get(index) : this.varArgType;
    }

    /**
     * @return A signature for documentation, such as "math.round(number, [whole number])"
     */
    public String signature() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < this.parameters.size(); i++) {
            var type = this.parameters.get(i).name();
            parts.add(i < this.minArgs ? type : "[" + type + "]");
        }
        if (this.varArgType != null)
            parts.add(this.varArgType.name() + "...");
        return this.name + "(" + String.join(", ", parts) + ")";
    }
}
