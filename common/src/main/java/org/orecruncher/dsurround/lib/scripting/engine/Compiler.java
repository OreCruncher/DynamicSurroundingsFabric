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
                        ScriptException.throwException(token.value, "Insufficient operands for function '%s'".formatted(token.value.lexeme()));
                    }
                    children.add(stack.pop());
                }

                // Reverse to restore original argument ordering
                Collections.reverse(children);

                stack.push(new Expression.Call(this.environment, token.value, children));
            } else if (token.value.type().isBinary()) {
                if (stack.size() < 2) {
                    ScriptException.throwException(token.value, "Insufficient operands for binary operator '%s'".formatted(token.value.lexeme()));
                }
                Expression right = stack.pop();
                Expression left = stack.pop();
                Expression result = this.optimizeBinaryOperation(left, token.value, right);

                stack.push(result);
            } else if (token.value.type().isUnary()) {
                if (stack.isEmpty()) {
                    ScriptException.throwException(token.value, "Insufficient operands for unary operator '%s'".formatted(token.value.lexeme()));
                }
                Expression right = stack.pop();
                var result = this.optimizeUnaryOperation(token.value, right);

                stack.push(result);
            } else if (Expression.isIdentifier(token.value)) {
                stack.push(new Expression.Variable(this.environment, token.value));
            } else if (Expression.isLiteral(token.value)) {
                stack.push(new Expression.Literal(this.environment, token.value));
            }
        }

        if (stack.size() != 1) {
            ScriptException.throwException("Extra unused tokens remain on stack.");
        }

        return stack.pop();
    }

    private Expression optimizeBinaryOperation(Expression left, Token operator, Expression right) {
        switch (operator.type()) {
            case STAR: {
                // Optimize for multiplying by 0. Result will be zero, so we can just return
                // the appropriate operand.
                if (left instanceof Expression.Literal l) {
                    if (l.value instanceof Number number && number.doubleValue() == 0) {
                        return l;
                    }
                }

                if (right instanceof Expression.Literal r) {
                    if (r.value instanceof Number number && number.doubleValue() == 0) {
                        return r;
                    }
                }
            }
            break;
            case CONDITIONAL_OR: {
                // Optimize for literal true
                if (left instanceof Expression.Literal l) {
                    var v = ScriptHelpers.toBoolean(l.value);
                    if (v) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, l.token.line(), l.token.position());
                        return new Expression.Literal(this.environment, newToken);
                    }
                }

                if (right instanceof Expression.Literal r) {
                    var v = ScriptHelpers.toBoolean(r.value);
                    if (v) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, r.token.line(), r.token.position());
                        return new Expression.Literal(this.environment, newToken);
                    }
                }

                // What if both are false
                if (left instanceof Expression.Literal l) {
                    if (right instanceof Expression.Literal r) {
                        var lv = ScriptHelpers.toBoolean(l.value);
                        var rv = ScriptHelpers.toBoolean(r.value);
                        if (!(lv || rv)) {
                            var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, operator.line(), operator.position());
                            return new Expression.Literal(this.environment, newToken);
                        }
                    }
                }
            }
            break;
            case CONDITIONAL_AND: {
                // Optimize for literal false
                if (left instanceof Expression.Literal l) {
                    var v = ScriptHelpers.toBoolean(l.value);
                    if (!v) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, l.token.line(), l.token.position());
                        return new Expression.Literal(this.environment, newToken);
                    }
                }

                if (right instanceof Expression.Literal r) {
                    var v = ScriptHelpers.toBoolean(r.value);
                    if (!v) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, r.token.line(), r.token.position());
                        return new Expression.Literal(this.environment, newToken);
                    }
                }

                // What if both are true
                if (left instanceof Expression.Literal l) {
                    if (right instanceof Expression.Literal r) {
                        var lv = ScriptHelpers.toBoolean(l.value);
                        var rv = ScriptHelpers.toBoolean(r.value);
                        if (lv && rv) {
                            var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, operator.line(), operator.position());
                            return new Expression.Literal(this.environment, newToken);
                        }
                    }
                }
            }
            break;
        }
        return new Expression.Binary(this.environment, left, operator, right);
    }

    private Expression optimizeUnaryOperation(Token operator, Expression right) {

        switch (operator.type()) {
            case NOT: {
                // Optimize inverting literal true and false
                if (right instanceof Expression.Literal r) {
                    var v = ScriptHelpers.toBoolean(r.value);
                    Token newToken;
                    if (v) {
                        newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, r.token.line(), r.token.position());
                    } else {
                        newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, r.token.line(), r.token.position());
                    }
                    return new Expression.Literal(this.environment, newToken);
                }

                // Optimize multiple NOT operations as they can cancel each other out
                if (right instanceof Expression.Unary u) {
                    if (u.operator.type() == TokenType.NOT) {
                        // Basically we can promote the operand of the target canceling the NOT operations
                        // out
                        return u.right;
                    }
                }
            }
            break;
        }
        return new Expression.Unary(this.environment, operator, right);
    }

    Expression compile(String script) {
        var scanner = new Scanner(script);
        var tokens = scanner.scanTokens();
        var rpnTokens = new RpnConverter(this.environment).infixToRpn(tokens);
        return translate(rpnTokens);
    }
}
