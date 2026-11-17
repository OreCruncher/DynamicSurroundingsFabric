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

    final Map<String, ScriptFunction> functions;
    final Map<String, ScriptVariable> variables;

    // The config files' definitions. A reload fills a staged environment's (see staged()) and hands them over whole
    // with one write (adopt), so a script compiling on another thread meanwhile sees the old set or the new, never
    // part of one
    private volatile ConfigLayer defined = ConfigLayer.empty();

    Environment() {
        this.functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.variables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    private Environment(Environment builtIn) {
        // Shared, and only read: the built-in names are all defined before any config definitions are made
        this.functions = builtIn.functions;
        this.variables = builtIn.variables;
    }

    /**
     * An environment with the same built-in names and no config definitions, for a reload to define them in before
     * {@link #adopt} hands them over
     */
    Environment staged() {
        return new Environment(this);
    }

    /**
     * Takes a staged environment's config definitions in place of this one's, all at once
     */
    void adopt(Environment staged) {
        this.defined = staged.defined;
    }

    boolean isFunction(Token token) {
        return this.functions.containsKey(token.lexeme()) || this.defined.functions().containsKey(token.lexeme());
    }

    public ScriptFunction getFunction(Token token) {
        var function = this.functions.get(token.lexeme());
        if (function == null)
            function = this.defined.functions().get(token.lexeme());
        if (function != null)
            return function;
        ScriptException.throwException(token, "Unable to locate function '%s'".formatted(token.lexeme()));
        return null;
    }

    public IScriptVariable getVariable(Token token) {
        var variableHandler = this.variables.get(token.lexeme());
        if (variableHandler == null)
            variableHandler = this.defined.variables().get(token.lexeme());
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
     * Adds a config definition of a function, replacing any of the name
     */
    void define(ScriptFunction function) {
        var name = function.name();
        checkDefName(name);
        this.defined.variables().remove(name);
        this.defined.functions().put(name, function);
    }

    /**
     * Adds a config definition of a variable, replacing any of the name
     */
    void define(String name, IScriptVariable handler) {
        checkDefName(name);
        this.defined.functions().remove(name);
        this.defined.variables().put(name, ScriptVariable.from(name, handler));
    }

    private static void checkDefName(String name) {
        if (!isDefName(name))
            ScriptException.throwException("A config definition's name must start with '%s': '%s'".formatted(DEF_PREFIX, name));
    }

    // Concurrent: a staged set is filled on the reload's thread, and an adopted one read on any
    private record ConfigLayer(Map<String, ScriptFunction> functions, Map<String, ScriptVariable> variables) {
        static ConfigLayer empty() {
            return new ConfigLayer(new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER),
                    new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER));
        }
    }

    record ScriptVariable(String name, IScriptVariable handler) {
        public static ScriptVariable from(String name, IScriptVariable handler) {
            return new ScriptVariable(name, handler);
        }
    }
}
