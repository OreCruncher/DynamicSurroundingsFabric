package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.base.MoreObjects;
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
                this.checkNotAdjacent(prevToken, token);
                output.add(RpnToken.of(token));
            }
            // 2. Function Identifier
            else if (this.environment.isFunction(token)) {
                this.checkNotAdjacent(prevToken, token);
                operatorStack.push(token);
                argCounts.push(1); // Default to 1 argument
            }
            // 3. Argument Separator ','
            else if (token.type() == TokenType.COMMA) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PAREN) {
                    output.add(RpnToken.of(operatorStack.pop()));
                }
                // The '(' on top of the stack must belong to a function call. A comma inside a plain grouping
                // parenthesis, such as f((1, 2)), is an error rather than an extra argument to f.
                if (operatorStack.isEmpty() || argCounts.isEmpty() || !this.isFunctionParen(operatorStack)) {
                    ScriptException.throwException(token, "Comma outside of valid function parameters");
                }
                // A complete argument must come before each comma, as in f(, 1), f(1, , 2) or f(1 +, 2)
                if (!this.endsValue(prevToken)) {
                    ScriptException.throwException(token, "Unexpected ',' (missing argument?)");
                }
                // Increment argument counter for the active function
                argCounts.push(argCounts.pop() + 1);
            }
            // 4. Left Parenthesis '('
            else if (token.type() == TokenType.LEFT_PAREN) {
                // If there was no previous token, or if it was an operator or identifier (function)
                if (prevToken == null || prevToken.type().isOperator() || prevToken.type() == TokenType.LEFT_PAREN || prevToken.type() == TokenType.COMMA || this.environment.isFunction(prevToken)) {
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

                    // Check to ensure that a proper number of variables have been specified. Errors are reported at the
                    // function name, which is where the problem is, rather than at the closing parenthesis.
                    var definition = this.environment.getFunction(fnName);

                    int min = definition.minArgs();
                    int max = definition.maxArgs();
                    if (max < 0) {
                        // Variable arguments: only a minimum
                        if (count < min) {
                            ScriptException.throwException(fnName, "Mismatched variable arguments: expected at least %d but received %d".formatted(min, count));
                        }
                    } else if (min == max) {
                        // The count must exactly match the function arity
                        if (count != min) {
                            ScriptException.throwException(fnName, "Mismatched variable arguments: expected %d but received %d".formatted(min, count));
                        }
                    } else if (count < min || count > max) {
                        // Optional parameters: a range
                        ScriptException.throwException(fnName, "Mismatched variable arguments: expected %d to %d but received %d".formatted(min, max, count));
                    }

                    output.add(RpnToken.of(fnName, count));
                }
            }
            // 6. Operators
            else if (token.type().isOperator()) {
                if (token.type().isUnaryOperator() && prevToken != null && prevToken.type() != TokenType.LEFT_PAREN && prevToken.type() != TokenType.COMMA && !prevToken.type().isOperator()) {
                    ScriptException.throwException(token, "Unexpected character '%s'".formatted(token.lexeme()));
                }
                // A binary operator needs a value on its left. Without this check, "|| 1 1" converts to the valid
                // RPN "1 1 ||" and compiles as "1 || 1".
                if (token.type().isBinaryOperator() && !this.endsValue(prevToken)) {
                    ScriptException.throwException(token, "Unexpected '%s' (missing left operand?)".formatted(token.lexeme()));
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

    /**
     * Reports a value that directly follows another value with no operator between them, such as the 'y' in
     * "f(x) y". Catching this here, rather than from the leftover stack in validate(), reports the stray piece
     * itself: once converted, "1 + 2 3" becomes "1 2 3 +", where the '+' appears to join 2 and 3. It also rejects
     * scripts whose RPN happens to be well-formed even though the infix is not, such as "true x / 's' /".
     */
    private void checkNotAdjacent(Token prevToken, Token token) {
        if (this.endsValue(prevToken)) {
            ScriptException.throwException(token, "Unexpected '%s' (missing operator?)".formatted(token.lexeme()));
        }
    }

    /**
     * Determines if the token completes a value: an operand, or the ')' closing a group or call.
     */
    private boolean endsValue(Token token) {
        return token != null && (this.isOperand(token) || token.type() == TokenType.RIGHT_PAREN);
    }

    /**
     * Determines if the '(' at the top of the operator stack opens a function call's argument list.
     */
    private boolean isFunctionParen(Deque<Token> operatorStack) {
        Iterator<Token> it = operatorStack.iterator();
        if (!it.hasNext() || it.next().type() != TokenType.LEFT_PAREN)
            return false;
        return it.hasNext() && this.environment.isFunction(it.next());
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
        // Simulates evaluation of the RPN. Each stack entry holds the first token (in source order) of the
        // sub-expression it represents, so leftover entries can be reported where they start in the script.
        Deque<Token> stack = new ArrayDeque<>();

        for (RpnToken token : tokens) {
            // Case 1: Function call
            if (token.isFunction) {
                if (stack.size() < token.argCount) {
                    ScriptException.throwException(token.token(), "Expected %d operands, but found %d".formatted(token.argCount, stack.size()));
                }
                for (int i = 0; i < token.argCount; i++)
                    stack.pop();
                // The function name comes before its arguments
                stack.push(token.token());
            }
            // Case 2: Unary Operator
            else if (token.token().type().isUnaryOperator()) {
                if (stack.isEmpty()) {
                    ScriptException.throwException(token.token(), "Insufficient operands for unary operator");
                }
                // Prefix operator: it comes before its operand
                stack.pop();
                stack.push(token.token());
            }
            // Case 3: Binary Operator
            else if (token.token().type().isBinaryOperator()) {
                if (stack.size() < 2) {
                    ScriptException.throwException(token.token(), "Expected 2 operands, but found %d".formatted(stack.size()));
                }
                // The result starts where the left operand starts
                stack.pop();
            }
            // Case 4: Operand
            else {
                stack.push(token.token());
            }
        }

        if (stack.size() > 1) {
            // Backstop: adjacent values are normally reported during conversion (see checkNotAdjacent). Entries are
            // in source order from the bottom of the stack; report where the second one starts.
            var firstExtra = stack.stream().skip(stack.size() - 2).findFirst().orElseThrow();
            ScriptException.throwException(firstExtra, "Malformed expression: %d items remain on stack instead of 1".formatted(stack.size()));
        } else if (stack.isEmpty()) {
            ScriptException.throwException("Malformed expression: no value produced");
        }
    }

    // Helper class to represent function tokens with argument counts
    record RpnToken(Token token, int argCount, boolean isFunction) {

        @Override
        public @NotNull String toString() {
            var builder = MoreObjects.toStringHelper(this)
                    .add("isFunction", this.isFunction)
                    .add("lexeme", this.token.lexeme());
            if (this.isFunction) {
                builder.add("args", this.argCount);
            }
            return builder.toString();
        }

        public static RpnToken of(Token token) {
            return new RpnToken(token, 0, false);
        }

        public static RpnToken of(Token token, int argCount) {
            return new RpnToken(token, argCount, true);
        }
    }
}