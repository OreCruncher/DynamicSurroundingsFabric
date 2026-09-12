package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.IScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.*;

final class Environment {

    final Map<String, FunctionDefinition> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, ScriptVariable> variables = new  TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    Environment() {
    }

    boolean isFunction(Token token) {
        return this.functions.containsKey(token.lexeme());
    }

    IScriptFunction getFunctionHandler(Token token ) {
        var functionDefinition = this.functions.get(token.lexeme());
        if (functionDefinition != null)
            return functionDefinition.handler;
        ScriptException.throwException(token, "Unable to locate function handler for '%s'".formatted(token.lexeme()));
        return null;
    }

    IScriptVariable getVariable(Token token) {
        var variableHandler = this.variables.get(token.lexeme());
        if (variableHandler != null)
            return variableHandler.handler;
        ScriptException.throwException(token, "Unable to locate variable '%s'".formatted(token.lexeme()));
        return null;
    }

    void defineFunction(String name, int arity, IScriptFunction handler) {
        if (Definitions.KEYWORDS.containsKey(name))
            ScriptException.throwException("Cannot use a keyword to name a function '%s'".formatted(name));
        if (this.functions.containsKey(name))
            ScriptException.throwException("Function already defined for name '%s'".formatted(name));
        if (this.variables.containsKey(name))
            ScriptException.throwException("A variable has been previously defined with the name '%s'".formatted(name));
        this.functions.put(name, FunctionDefinition.from(name, arity, handler));
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

    record FunctionDefinition(String name, int arity, IScriptFunction handler){
        public static FunctionDefinition from(String name, int arity, IScriptFunction handler) {
            return new FunctionDefinition(name, arity, handler);
        }
    }

    record ScriptVariable(String name, IScriptVariable handler) {
        public static ScriptVariable from(String name, IScriptVariable handler) {
            return new ScriptVariable(name, handler);
        }
    }
}
