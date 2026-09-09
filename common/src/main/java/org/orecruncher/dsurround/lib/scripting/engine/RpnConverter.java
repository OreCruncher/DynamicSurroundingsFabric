package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

record RpnConverter(Environment environment) {

    /**
     * Converts a list of infix tokens to an RPN list.
     */
    public List<RpnToken> infixToRpn(List<Token> tokens) {
        List<RpnToken> output = new ArrayList<>();
        Deque<Token> operatorStack = new ArrayDeque<>();
        Deque<Integer> argCounts = new ArrayDeque<>();

        Token prevToken = null;

        for (var token : tokens) {
            // No further tokens in the stream
            if (token.type() == TokenType.EOF)
                break;

            // 1. Numbers / Variables
            if (isOperand(token)) {
                output.add(new RpnToken(token));
            }
            // 2. Function Identifier
            else if (this.environment.isFunction(token)) {
                operatorStack.push(token);
                argCounts.push(1); // Default to 1 argument
            }
            // 3. Argument Separator ','
            else if (token.type() == TokenType.COMMA) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PAREN) {
                    output.add(new RpnToken(operatorStack.pop()));
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
                if (prevToken == null || this.environment.isFunction(prevToken) || Definitions.isOperator(prevToken) || prevToken.type() == TokenType.LEFT_PAREN) {
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
                    output.add(new RpnToken(operatorStack.pop()));
                }

                if (operatorStack.isEmpty()) {
                    ScriptException.throwException(token, "Mismatched parenthesis: missing '('");
                }
                operatorStack.pop(); // Discard '('

                // If top of stack is a function, pop it with its arg count
                if (!operatorStack.isEmpty() && this.environment.isFunction(operatorStack.peek())) {
                    Token fnName = operatorStack.pop();
                    int count = argCounts.pop();
                    output.add(new RpnToken(fnName, count));
                }
            }
            // 6. Unary/Binary Operators
            else if (Definitions.isOperator(token)) {
                int currPrec = Definitions.PRECEDENCE.get(token.type());
                boolean isRightAssoc = Definitions.RIGHT_ASSOCIATIVE.contains(token.type());

                while (!operatorStack.isEmpty() && Definitions.PRECEDENCE.containsKey(operatorStack.peek().type())) {
                    Token topOp = operatorStack.peek();
                    int topPrec = Definitions.PRECEDENCE.get(topOp.type());

                    if ((!isRightAssoc && topPrec >= currPrec) || (isRightAssoc && topPrec > currPrec)) {
                        output.add(new RpnToken(operatorStack.pop()));
                    } else {
                        break;
                    }
                }
                operatorStack.push(token);
            } else {
                ScriptException.throwException(token, "Unrecognized token: " + token);
            }

            prevToken = token;
        }

        // 7. Pop remaining operators from stack
        while (!operatorStack.isEmpty()) {
            Token op = operatorStack.pop();
            if (op.type() == TokenType.LEFT_PAREN || op.type() == TokenType.RIGHT_PAREN) {
                ScriptException.throwException(op, "Mismatched parentheses");
            }
            output.add(new RpnToken(op));
        }

        return output;
    }

    private boolean isOperand(Token token) {
        return !this.environment.isFunction(token) &&
                !Definitions.isBinaryOperator(token) &&
                !Definitions.isUnaryOperator(token) &&
                token.type() != TokenType.LEFT_PAREN &&
                token.type() != TokenType.RIGHT_PAREN &&
                token.type() != TokenType.COMMA;
    }

    // Helper class to represent function tokens with argument counts
    static class RpnToken {
        public Token value;
        public int argCount;
        public boolean isFunction;

        public RpnToken(Token value) {
            this.value = value;
            this.isFunction = false;
            this.argCount = 0;
        }

        public RpnToken(Token value, int argCount) {
            this.value = value;
            this.argCount = argCount;
            this.isFunction = true;
        }

        @Override
        public String toString() {
            return this.isFunction ? (this.value.lexeme() + ":" + this.argCount) : this.value.lexeme();
        }
    }
}