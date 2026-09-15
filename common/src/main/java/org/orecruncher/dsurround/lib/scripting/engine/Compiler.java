package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

record Compiler(Environment environment) {

    Expression translate(List<RpnConverter.RpnToken> tokens) {
        Deque<Expression> stack = new ArrayDeque<>();

        // Iterate through the tokens generating a syntax tree
        for (var token : tokens) {
            if (token.isFunction()) {
                List<Expression> children = new ArrayList<>();
                // Pop N arguments off the stack (popped in reverse order)
                for (int i = 0; i < token.argCount(); i++) {
                    if (stack.isEmpty()) {
                        ScriptException.throwException(token.token(), "Insufficient operands for function '%s'".formatted(token.token().lexeme()));
                    }
                    children.add(stack.pop());
                }

                // Reverse to restore original argument ordering
                Collections.reverse(children);

                stack.push(new Expression.Call(this.environment, token.token(), children));
            } else if (token.token().type().isBinaryOperator()) {
                if (stack.size() < 2) {
                    ScriptException.throwException(token.token(), "Insufficient operands for binary operator '%s'".formatted(token.token().lexeme()));
                }
                Expression right = stack.pop();
                Expression left = stack.pop();
                Expression result = this.optimizeBinaryOperation(left, token.token(), right);

                stack.push(result);
            } else if (token.token().type().isUnaryOperator()) {
                if (stack.isEmpty()) {
                    ScriptException.throwException(token.token(), "Insufficient operands for unary operator '%s'".formatted(token.token().lexeme()));
                }
                Expression right = stack.pop();
                var result = this.optimizeUnaryOperation(token.token(), right);

                stack.push(result);
            } else if (Expression.isIdentifier(token.token())) {
                stack.push(new Expression.Variable(this.environment, token.token()));
            } else if (Expression.isLiteral(token.token())) {
                stack.push(new Expression.Literal(this.environment, token.token()));
            }
        }

        if (stack.size() != 1) {
            ScriptException.throwException("Extra unused tokens remain on stack");
        }

        return stack.pop();
    }

    private Expression optimizeBinaryOperation(Expression left, Token operator, Expression right) {
        switch (operator.type()) {
            case STAR: {
                // Optimize for multiplying by 0. Result will be zero, so we can just return
                // the appropriate operand.
                if (left instanceof Expression.Literal l && l.value instanceof Number number && number.doubleValue() == 0) {
                    return l;
                }

                if (right instanceof Expression.Literal r && r.value instanceof Number number && number.doubleValue() == 0) {
                    return r;
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
                if (left instanceof Expression.Literal l &&  right instanceof Expression.Literal r) {
                    var lv = ScriptHelpers.toBoolean(l.value);
                    var rv = ScriptHelpers.toBoolean(r.value);
                    if (!(lv || rv)) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, operator.line(), operator.position());
                        return new Expression.Literal(this.environment, newToken);
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
                if (left instanceof Expression.Literal l && right instanceof Expression.Literal r) {
                    var lv = ScriptHelpers.toBoolean(l.value);
                    var rv = ScriptHelpers.toBoolean(r.value);
                    if (lv && rv) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, operator.line(), operator.position());
                        return new Expression.Literal(this.environment, newToken);
                    }
                }
            }
            break;
            case PLUS: {
                // If both operands are literals see if the expression can be reduced
                if (left instanceof Expression.Literal l && right instanceof Expression.Literal r) {
                    // If numbers are involved
                    if (l.token.type() == TokenType.NUMBER && r.token.type() == TokenType.NUMBER) {
                        if (l.eval().equals(0.0)) {
                            return right;
                        } else if (r.eval().equals(0.0)) {
                            return left;
                        }
                    }

                    // If strings are involved
                    if (l.token.type() == TokenType.STRING || r.token.type() == TokenType.STRING) {
                        // We do some concatenation
                        var result = l.eval().toString() + r.eval().toString();
                        var newToken = Token.from(TokenType.STRING, result, result, operator.line(), operator.position());
                        return new Expression.Literal(this.environment, newToken);
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
                if (right instanceof Expression.Unary u && u.operator.type() == TokenType.NOT) {
                    // Basically we can promote the operand of the target canceling the NOT operations
                    // out
                    return u.right;
                }
            }
            break;
            case NEG: {
                // Optimize negation of a numeric constant
                if (right instanceof Expression.Literal l) {
                    // Should be a number; negate the constant
                    if (l.token.type() != TokenType.NUMBER) {
                        ScriptException.throwException(l.token, "Number expected");
                    }
                    var n = -ScriptHelpers.toDouble(l.value);
                    var newToken = Token.from(TokenType.NUMBER, Double.toString(n), n, l.token.line(), l.token.position());
                    return new Expression.Literal(this.environment, newToken);
                }

                // Optimize multiple NEG operations as they can cancel each other out
                if (right instanceof Expression.Unary u && u.operator.type() == TokenType.NEG) {
                    // Basically we can promote the operand of the target canceling the NEG operations
                    // out
                    return u.right;
                }
            }
            break;
        }
        return new Expression.Unary(this.environment, operator, right);
    }

    Expression compile(String script) {
        var lexer = new Lexer(script);
        var tokens = lexer.getTokens();
        var rpnTokens = new RpnConverter(this.environment).infixToRpn(tokens);
        return this.translate(rpnTokens);
    }
}
