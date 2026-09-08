package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.IScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.*;

class Environment {

    final Map<String, FunctionDefinition> FUNCTIONS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final EnumMap<TokenType, Integer> PRECEDENCE = new EnumMap<>(TokenType.class);
    final Set<TokenType> RIGHT_ASSOCIATIVE = EnumSet.noneOf(TokenType.class);
    final Set<TokenType> BINARY_OPERATORS = EnumSet.noneOf(TokenType.class);
    final Set<TokenType> UNARY_OPERATORS = EnumSet.noneOf(TokenType.class);

    final Map<String, ScriptVariable> GLOBALS = new  TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    Environment() {
        this.initializePrecedenceTable();
        this.initializeRightAssociativityTable();
        this.initializeOperators();
        this.defineFunctions();
    }

    void initializePrecedenceTable() {
        PRECEDENCE.put(TokenType.CONDITIONAL_OR, 1);
        PRECEDENCE.put(TokenType.CONDITIONAL_AND, 2);
        PRECEDENCE.put(TokenType.EQUAL_EQUAL, 5);
        PRECEDENCE.put(TokenType.NOT_EQUAL, 5);
        PRECEDENCE.put(TokenType.GREATER, 6);
        PRECEDENCE.put(TokenType.GREATER_EQUAL, 6);
        PRECEDENCE.put(TokenType.LESS, 6);
        PRECEDENCE.put(TokenType.LESS_EQUAL, 6);
        PRECEDENCE.put(TokenType.PLUS, 7);
        PRECEDENCE.put(TokenType.MINUS, 7);
        PRECEDENCE.put(TokenType.STAR, 8);
        PRECEDENCE.put(TokenType.SLASH, 8);
        PRECEDENCE.put(TokenType.NOT,9);
    }

    void initializeRightAssociativityTable() {
        RIGHT_ASSOCIATIVE.add(TokenType.NOT);
    }

    void initializeOperators() {
        UNARY_OPERATORS.add(TokenType.NOT);

        BINARY_OPERATORS.addAll(
                List.of(TokenType.CONDITIONAL_OR,
                        TokenType.CONDITIONAL_AND,
                        TokenType.EQUAL_EQUAL,
                        TokenType.NOT_EQUAL,
                        TokenType.GREATER,
                        TokenType.GREATER_EQUAL,
                        TokenType.LESS,
                        TokenType.LESS_EQUAL,
                        TokenType.PLUS,
                        TokenType.MINUS,
                        TokenType.STAR,
                        TokenType.SLASH
                ));
    }

    void defineFunctions() {
    }

    boolean isFunction(Token token) {
        return FUNCTIONS.containsKey(token.lexeme());
    }

    boolean isBinaryOperator(Token token) {
        return BINARY_OPERATORS.contains(token.type());
    }

    boolean isUnaryOperator(Token token) {
        return UNARY_OPERATORS.contains(token.type());
    }

    boolean isOperator(Token token) {
        return isBinaryOperator(token) || isUnaryOperator(token);
    }

    IScriptFunction getFunctionHandler(Token token ) {
        var functionDefinition = FUNCTIONS.get(token.lexeme());
        if (functionDefinition != null)
            return functionDefinition.handler;
        ScriptException.error(token, "Unable to locate function handler for '%s'".formatted(token.lexeme()));
        return null;
    }

    IScriptVariable getVariable(Token token) {
        var variableHandler = GLOBALS.get(token.lexeme());
        if (variableHandler != null)
            return variableHandler.handler;
        ScriptException.error(token, "Unable to locate variable '%s'".formatted(token.lexeme()));
        return null;
    }

    void defineFunction(String name, int arity, IScriptFunction handler) {
        FUNCTIONS.put(name, FunctionDefinition.from(name, arity, handler));
    }

    void defineVariable(String name, IScriptVariable handler) {
        GLOBALS.put(name, ScriptVariable.from(name, handler));
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
