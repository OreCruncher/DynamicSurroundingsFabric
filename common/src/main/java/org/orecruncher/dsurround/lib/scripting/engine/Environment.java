// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.ScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * The names scripts can use. Two layers: the built-in one, defined in code once and never changed (a name defined
 * twice is an error), and the one for definitions from config files ({@code def.} names, see
 * {@link org.orecruncher.dsurround.lib.scripting.ScriptDefinitions}), replaced as a whole on each reload. Every config
 * definition is named under {@link #DEF_PREFIX} and no built-in one may be, so they can never clash.
 */
public final class Environment {

    /**
     * The start of every name defined in a config file, and of none defined in code
     */
    public static final String DEF_PREFIX = "def.";

    final Map<String, ScriptFunction> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, ScriptVariable> variables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // The config files' definitions: concurrent, as scripts may compile on other threads while a reload fills them
    private volatile Map<String, ScriptFunction> definedFunctions = newFunctionLayer();
    private volatile Map<String, ScriptVariable> definedVariables = newVariableLayer();

    Environment() {
    }

    boolean isFunction(Token token) {
        return this.functions.containsKey(token.lexeme()) || this.definedFunctions.containsKey(token.lexeme());
    }

    public ScriptFunction getFunction(Token token) {
        var function = this.functions.get(token.lexeme());
        if (function == null)
            function = this.definedFunctions.get(token.lexeme());
        if (function != null)
            return function;
        ScriptException.throwException(token, "Unable to locate function '%s'".formatted(token.lexeme()));
        return null;
    }

    public IScriptVariable getVariable(Token token) {
        var variableHandler = this.variables.get(token.lexeme());
        if (variableHandler == null)
            variableHandler = this.definedVariables.get(token.lexeme());
        if (variableHandler != null)
            return variableHandler.handler;
        ScriptException.throwException(token, "Unable to locate variable '%s'".formatted(token.lexeme()));
        return null;
    }

    void defineFunction(ScriptFunction function) {
        var name = function.name();
        checkBuiltInName(name, "function");
        if (this.functions.containsKey(name))
            ScriptException.throwException("Function already defined for name '%s'".formatted(name));
        if (this.variables.containsKey(name))
            ScriptException.throwException("A variable has been previously defined with the name '%s'".formatted(name));
        this.functions.put(name, function);
    }

    void defineVariable(String name, IScriptVariable handler) {
        checkBuiltInName(name, "variable");
        if (this.variables.containsKey(name))
            ScriptException.throwException("Variable already defined for name '%s'".formatted(name));
        if (this.functions.containsKey(name))
            ScriptException.throwException("A function has been previously defined with the name '%s'".formatted(name));
        this.variables.put(name, ScriptVariable.from(name, handler));
    }

    private static void checkBuiltInName(String name, String what) {
        if (Definitions.KEYWORDS.containsKey(name))
            ScriptException.throwException("Cannot use a keyword to name a %s '%s'".formatted(what, name));
        if (isDefName(name))
            ScriptException.throwException("'%s' is kept for config definitions: a %s defined in code can't start with it"
                    .formatted(DEF_PREFIX, what));
    }

    /**
     * Whether a name is one of the config files' ({@code def.}...)
     */
    public static boolean isDefName(String name) {
        return name.regionMatches(true, 0, DEF_PREFIX, 0, DEF_PREFIX.length());
    }

    /**
     * Drops every config definition, to define them again
     */
    void clearDefinitions() {
        this.definedFunctions = newFunctionLayer();
        this.definedVariables = newVariableLayer();
    }

    /**
     * Adds a config definition of a function, replacing any of the name
     */
    void define(ScriptFunction function) {
        var name = function.name();
        checkDefName(name);
        this.definedVariables.remove(name);
        this.definedFunctions.put(name, function);
    }

    /**
     * Adds a config definition of a variable, replacing any of the name
     */
    void define(String name, IScriptVariable handler) {
        checkDefName(name);
        this.definedFunctions.remove(name);
        this.definedVariables.put(name, ScriptVariable.from(name, handler));
    }

    private static void checkDefName(String name) {
        if (!isDefName(name))
            ScriptException.throwException("A config definition's name must start with '%s': '%s'".formatted(DEF_PREFIX, name));
    }

    private static Map<String, ScriptFunction> newFunctionLayer() {
        return new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static Map<String, ScriptVariable> newVariableLayer() {
        return new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    record ScriptVariable(String name, IScriptVariable handler) {
        public static ScriptVariable from(String name, IScriptVariable handler) {
            return new ScriptVariable(name, handler);
        }
    }
}
