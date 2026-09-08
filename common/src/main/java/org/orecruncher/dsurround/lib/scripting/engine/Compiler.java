package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

record Compiler(Environment environment) {

    Expression translate(List<RpnConverter.RpnToken> tokens) {
        Deque<Expression> stack = new ArrayDeque<>();

        // Iterate through the tokens generating a syntax tree
        for (var token : tokens) {
            if (token.isFunction) {
                List<Expression> children = new ArrayList<>();
                // Pop N arguments off the stack (popped in reverse order)
                for (int i = 0; i < token.argCount; i++) {
                    if (stack.isEmpty()) {
                        ScriptException.error(token.value, "Invalid RPN: Insufficient operands for function '%s'".formatted(token.value.lexeme()));
                    }
                    children.add(stack.pop());
                }

                // Reverse to restore original argument ordering
                Collections.reverse(children);

                stack.push(new Expression.Call(this.environment, token.value, children));
            } else if (this.environment.isBinaryOperator(token.value)) {
                if (stack.size() < 2) {
                    ScriptException.error(token.value, "Invalid RPN: Insufficient operands for binary operator '%s'".formatted(token.value.lexeme()));
                }
                Expression right = stack.pop();
                Expression left = stack.pop();
                stack.push(new Expression.Binary(this.environment, left, token.value, right));
            } else if (this.environment.isUnaryOperator(token.value)) {
                if (stack.isEmpty()) {
                    ScriptException.error(token.value, "Invalid RPN: Insufficient operands for unary operator '%s'".formatted(token.value.lexeme()));
                }
                Expression right = stack.pop();
                stack.push(new Expression.Unary(this.environment, token.value, right));
            } else if (token.value.type() == TokenType.IDENTIFIER) {
                stack.push(new Expression.Variable(this.environment, token.value));
            } else if (token.value.type() == TokenType.NUMBER || token.value.type() == TokenType.STRING) {
                stack.push(new Expression.Literal(this.environment, token.value));
            } else if (token.value.type() == TokenType.TRUE || token.value.type() == TokenType.FALSE) {
                stack.push(new Expression.Literal(this.environment, token.value));
            }
        }

        if (stack.size() != 1) {
            ScriptException.error("Invalid RPN list: Extra unused tokens remain on stack.");
        }

        return stack.pop();
    }

    ExpressionTree compile(String script) {
        var scanner = new Scanner(script);
        var tokens = scanner.scanTokens();
        var rpnTokens = new RpnConverter(this.environment).infixToRpn(tokens);
        var expression = translate(rpnTokens);
        return new ExpressionTree(expression);
    }
}
