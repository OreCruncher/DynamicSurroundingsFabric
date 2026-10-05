package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;
import org.orecruncher.dsurround.lib.scripting.engine.Environment;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

public record Variable(Token name, IScriptVariable variable) implements Expression {

    public static Variable from(Environment environment, Token name) {
        var variable = environment.getVariable(name);
        return new Variable(name, variable);
    }

    @Override
    public Object eval() {
        try {
            return this.variable.getValue();
        } catch (ScriptException e) {
            throw ScriptException.locate(this.name, e);
        }
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.name.lexeme())
                .toString();
    }
}
