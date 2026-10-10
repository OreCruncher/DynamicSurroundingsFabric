package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.ScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.*;

public final class Environment {

    final Map<String, ScriptFunction> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, ScriptVariable> variables = new  TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    Environment() {
    }

    boolean isFunction(Token token) {
        return this.functions.containsKey(token.lexeme());
    }

    public ScriptFunction getFunction(Token token) {
        var function = this.functions.get(token.lexeme());
        if (function != null)
            return function;
        ScriptException.throwException(token, "Unable to locate function '%s'".formatted(token.lexeme()));
        return null;
    }

    public IScriptVariable getVariable(Token token) {
        var variableHandler = this.variables.get(token.lexeme());
        if (variableHandler != null)
            return variableHandler.handler;
        ScriptException.throwException(token, "Unable to locate variable '%s'".formatted(token.lexeme()));
        return null;
    }

    void defineFunction(ScriptFunction function) {
        var name = function.name();
        if (Definitions.KEYWORDS.containsKey(name))
            ScriptException.throwException("Cannot use a keyword to name a function '%s'".formatted(name));
        if (this.functions.containsKey(name))
            ScriptException.throwException("Function already defined for name '%s'".formatted(name));
        if (this.variables.containsKey(name))
            ScriptException.throwException("A variable has been previously defined with the name '%s'".formatted(name));
        this.functions.put(name, function);
    }

    void defineVariable(String name, IScriptVariable handler) {
        if (Definitions.KEYWORDS.containsKey(name))
            ScriptException.throwException("Cannot use a keyword to name a variable '%s'".formatted(name));
        if (this.variables.containsKey(name))
            ScriptException.throwException("Variable already defined for name '%s'".formatted(name));
        if (this.functions.containsKey(name))
            ScriptException.throwException("A function has been previously defined with the name '%s'".formatted(name));
        this.variables.put(name, ScriptVariable.from(name, handler));
    }

    record ScriptVariable(String name, IScriptVariable handler) {
        public static ScriptVariable from(String name, IScriptVariable handler) {
            return new ScriptVariable(name, handler);
        }
    }
}
