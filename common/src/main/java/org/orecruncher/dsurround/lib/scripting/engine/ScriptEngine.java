// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.*;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ScriptEngine implements IConfigureDefinition {

    final Environment environment;
    final Compiler compiler;

    public ScriptEngine() {
        this.environment = new Environment();
        this.compiler = new Compiler(this.environment);
        LibraryFunctions.configure(this);
        MathFunctions.configure(this);
    }

    /**
     * Compiles the script into a syntax tree that can be used for evaluation. Expression instances should be cached
     * for performance.
     * @param script The script compile
     * @return An expression tree use to execute the script logic and obtain a value
     */
    public Expression compile(String script) {
        Preconditions.checkNotNull(script);
        return this.compiler.compile(script);
    }

    @Override
    public void defineVariable(@NotNull String name, @NotNull IScriptVariable delegate) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(delegate);
        this.environment.defineVariable(name, delegate);
    }

    @Override
    public void defineFunction(@NotNull ScriptFunction function) {
        Preconditions.checkNotNull(function);
        this.environment.defineFunction(function);
    }

    /**
     * Drops every config definition ({@code def.} names), to define them again
     */
    public void clearDefinitions() {
        this.environment.clearDefinitions();
    }

    /**
     * Adds a config definition of a constant or other variable; its name starts with {@code def.}
     */
    public void define(@NotNull String name, @NotNull IScriptVariable variable) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(variable);
        this.environment.define(name, variable);
    }

    /**
     * Adds a config definition of a function; its name starts with {@code def.}
     */
    public void define(@NotNull ScriptFunction function) {
        Preconditions.checkNotNull(function);
        this.environment.define(function);
    }

    /**
     * The config definition names ({@code def.}...) a script uses, in the order it uses them first
     *
     * @throws ScriptException if the script can't be read
     */
    public static Set<String> definitionsUsed(String script) {
        var names = new LinkedHashSet<String>();
        for (var token : new Lexer(script).getTokens())
            if (token.type() == TokenType.IDENTIFIER && Environment.isDefName(token.lexeme()))
                names.add(token.lexeme());
        return names;
    }

}
