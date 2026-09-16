package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;

import java.util.*;

record RpnConverter(Environment environment) {

    /**
     * Converts a list of infix tokens to an RPN list.
     */
    public List<RpnToken> infixToRpn(List<Token> tokens) {
        var rpn = this.toRpn(tokens);
        this.validate(rpn);
        return rpn;
    }

    private List<RpnToken> toRpn(List<Token> tokens) {

        if (tokens.size() < 2) {
            ScriptException.throwException("Empty script");
        }

        List<RpnToken> output = new ArrayList<>();
        Deque<Token> operatorStack = new ArrayDeque<>();
        Deque<Integer> argCounts = new ArrayDeque<>();

        Token prevToken = null;

        for (var token : tokens) {
            // No further tokens in the stream
            if (token.type() == TokenType.EOF)
                break;

            // 1. Numbers / Variables
            if (this.isOperand(token)) {
                output.add(RpnToken.of(token));
            }
            // 2. Function Identifier
            else if (this.environment.isFunction(token)) {
                operatorStack.push(token);
                argCounts.push(1); // Default to 1 argument
            }
            // 3. Argument Separator ','
            else if (token.type() == TokenType.COMMA) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PAREN) {
                    output.add(RpnToken.of(operatorStack.pop()));
                }
                if (operatorStack.isEmpty() || argCounts.isEmpty()) {
                    ScriptException.throwException(token, "Comma outside of valid function parameters");
                }
                // Increment argument counter for the active function
                argCounts.push(argCounts.pop() + 1);
            }
            // 4. Left Parenthesis '('
            else if (token.type() == TokenType.LEFT_PAREN) {
                // If there was no previous token, or if it was an operator or identifier (function)
                if (prevToken == null || prevToken.type().isOperator() || prevToken.type() == TokenType.LEFT_PAREN || this.environment.isFunction(prevToken)) {
                    operatorStack.push(token);
                } else {
                    ScriptException.throwException(token, "Unexpected '(' (undefined function/typo?)");
                }
            }
            // 5. Right Parenthesis ')'
            else if (token.type() == TokenType.RIGHT_PAREN) {
                if (prevToken == null) {
                    ScriptException.throwException(token, "Mismatched parenthesis");
                }

                // Edge case: zero-argument function call fn()
                if (prevToken.type() == TokenType.LEFT_PAREN && operatorStack.size() >= 2) {
                    Iterator<Token> it = operatorStack.iterator();
                    it.next(); // Skip '('
                    if (this.environment.isFunction(it.next())) {
                        argCounts.pop();
                        argCounts.push(0);
                    }
                }

                while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PAREN) {
                    output.add(RpnToken.of(operatorStack.pop()));
                }

                if (operatorStack.isEmpty()) {
                    ScriptException.throwException(token, "Mismatched parenthesis: missing '('");
                }
                operatorStack.pop(); // Discard '('

                // If top of stack is a function, pop it with its arg count
                if (!operatorStack.isEmpty() && this.environment.isFunction(operatorStack.peek())) {
                    Token fnName = operatorStack.pop();
                    int count = argCounts.pop();

                    // Check to ensure that a proper number of variables have been specified
                    var definition = this.environment.getFunctionDefinition(fnName);
                    if (definition == null) {
                        // This should not happen
                        ScriptException.throwException(token, "Unable to locate function definition in environment when it should be present: %s".formatted(token.lexeme()));
                    }

                    if (definition.hasVarArgs()) {
                        // arity indicates the minimum required parameters for a vararg function
                        if (count < definition.arity()) {
                            ScriptException.throwException(token, "Mismatched variable arguments: expected at least %d but received %d".formatted(definition.arity(), count));
                        }
                    } else if (count != definition.arity()) {
                        // The count must exactly match the function arity
                        ScriptException.throwException(token, "Mismatched variable arguments: expected %d but received %d".formatted(definition.arity(), count));
                    }

                    output.add(RpnToken.of(fnName, count));
                }
            }
            // 6. Operators
            else if (token.type().isOperator()) {
                if (token.type().isUnaryOperator() && prevToken != null && prevToken.type() != TokenType.LEFT_PAREN && !prevToken.type().isOperator()) {
                    ScriptException.throwException(token, "Unexpected character '%s'".formatted(token.lexeme()));
                }
                int currPrec = token.type().getPrecedence();
                boolean isRightAssoc = token.type().isRightAssociative();

                while (!operatorStack.isEmpty() && operatorStack.peek().type().getPrecedence() != TokenType.NO_PRECEDENCE) {
                    Token topOp = operatorStack.peek();
                    int topPrec = topOp.type().getPrecedence();

                    if ((!isRightAssoc && topPrec >= currPrec) || (isRightAssoc && topPrec > currPrec)) {
                        output.add(RpnToken.of(operatorStack.pop()));
                    } else {
                        break;
                    }
                }
                operatorStack.push(token);
            } else {
                ScriptException.throwException(token, "Unrecognized token: %s".formatted(token));
            }

            prevToken = token;
        }

        // 7. Pop remaining operators from stack
        while (!operatorStack.isEmpty()) {
            Token op = operatorStack.pop();
            if (op.type() == TokenType.LEFT_PAREN || op.type() == TokenType.RIGHT_PAREN) {
                ScriptException.throwException(op, "Mismatched parentheses");
            }
            output.add(RpnToken.of(op));
        }

        if (output.isEmpty()) {
            ScriptException.throwException("Logic not detected in script");
        }

        return output;
    }

    private boolean isOperand(Token token) {
        return !this.environment.isFunction(token) &&
                !token.type().isBinaryOperator() &&
                !token.type().isUnaryOperator() &&
                token.type() != TokenType.LEFT_PAREN &&
                token.type() != TokenType.RIGHT_PAREN &&
                token.type() != TokenType.COMMA;
    }

    private void validate(List<RpnToken> tokens) {
        int stackDepth = 0;

        for (RpnToken token : tokens) {
            // Case 1: Function call
            if (token.isFunction) {
                if (stackDepth < token.argCount) {
                    ScriptException.throwException(token.token(), "Expected %d operands, but found %d".formatted(token.argCount, stackDepth));
                }
                stackDepth -= (token.argCount - 1);
            }
            // Case 2: Unary Operator
            else if (token.token().type().isUnaryOperator()) {
                if (stackDepth < 1) {
                    ScriptException.throwException(token.token(), "Insufficient operands for unary operator");
                }
            }
            // Case 3: Binary Operator
            else if (token.token().type().isBinaryOperator()) {
                if (stackDepth < 2) {
                    ScriptException.throwException(token.token(), "Expected 2 operands, but found %d".formatted(stackDepth));
                }
                stackDepth--;
            }
            // Case 4: Operand
            else {
                stackDepth++;
            }
        }

        if (stackDepth != 1) {
            ScriptException.throwException("Malformed expression: %d items remain on stack instead of 1".formatted(stackDepth));
        }
    }

    // Helper class to represent function tokens with argument counts
    record RpnToken(Token token, int argCount, boolean isFunction) {

        @Override
        public @NotNull String toString() {
            return this.isFunction ? (this.token.lexeme() + ":" + this.argCount) : this.token.lexeme();
        }

        public static RpnToken of(Token token) {
            return new RpnToken(token, 0, false);
        }

        public static RpnToken of(Token token, int argCount) {
            return new RpnToken(token, argCount, true);
        }
    }
}