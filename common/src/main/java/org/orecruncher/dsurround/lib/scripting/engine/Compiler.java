package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.engine.expression.*;

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

                stack.push(Call.from(this.environment, token.token(), children));
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
            } else if (TokenType.isIdentifier(token.token().type())) {
                stack.push(Variable.from(this.environment, token.token()));
            } else if (TokenType.isLiteral(token.token().type())) {
                stack.push(Literal.from(token.token()));
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
                if (left instanceof Literal l && l.value() instanceof Number number && number.doubleValue() == 0) {
                    return l;
                }

                if (right instanceof Literal r && r.value() instanceof Number number && number.doubleValue() == 0) {
                    return r;
                }
            }
            break;
            case CONDITIONAL_OR: {
                // Optimize for literal true
                if (left instanceof Literal(Token token, Object value)) {
                    var v = ScriptHelpers.toBoolean(value);
                    if (v) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, token.line(), token.position());
                        return Literal.from(newToken);
                    }
                }

                if (right instanceof Literal(Token token, Object value)) {
                    var v = ScriptHelpers.toBoolean(value);
                    if (v) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, token.line(), token.position());
                        return Literal.from(newToken);
                    }
                }

                // What if both are false
                if (left instanceof Literal(Token t1, Object leftValue) && right instanceof Literal(Token t2, Object rightValue)) {
                    var lv = ScriptHelpers.toBoolean(leftValue);
                    var rv = ScriptHelpers.toBoolean(rightValue);
                    if (!(lv || rv)) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, operator.line(), operator.position());
                        return Literal.from(newToken);
                    }
                }
            }
            break;
            case CONDITIONAL_AND: {
                // Optimize for literal false
                if (left instanceof Literal(Token token, Object value)) {
                    var v = ScriptHelpers.toBoolean(value);
                    if (!v) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, token.line(), token.position());
                        return Literal.from(newToken);
                    }
                }

                if (right instanceof Literal(Token token, Object value)) {
                    var v = ScriptHelpers.toBoolean(value);
                    if (!v) {
                        var newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, token.line(), token.position());
                        return Literal.from(newToken);
                    }
                }

                // What if both are true
                if (left instanceof Literal(Token t1, Object leftValue) && right instanceof Literal(Token t2, Object rightValue)) {
                    var lv = ScriptHelpers.toBoolean(leftValue);
                    var rv = ScriptHelpers.toBoolean(rightValue);
                    if (lv && rv) {
                        var newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, operator.line(), operator.position());
                        return Literal.from(newToken);
                    }
                }
            }
            break;
            case PLUS: {
                // If both operands are literals see if the expression can be reduced
                if (left instanceof Literal(Token leftToken, Object leftValue) && right instanceof Literal(Token rightToken, Object rightValue)) {
                    // If numbers are involved
                    if (leftToken.type() == TokenType.NUMBER && rightToken.type() == TokenType.NUMBER) {
                        if (leftValue.equals(0.0)) {
                            return right;
                        } else if (rightValue.equals(0.0)) {
                            return left;
                        }
                    }

                    // If strings are involved
                    if (leftToken.type() == TokenType.STRING || rightToken.type() == TokenType.STRING) {
                        // We do some concatenation
                        var result = leftValue.toString() + rightValue.toString();
                        var newToken = Token.from(TokenType.STRING, "'" + result + "'", result, operator.line(), operator.position());
                        return Literal.from(newToken);
                    }
                }
            }
            break;
        }
        return Binary.from(left, operator, right);
    }

    private Expression optimizeUnaryOperation(Token operator, Expression right) {

        switch (operator.type()) {
            case NOT: {
                // Optimize inverting literal true and false
                if (right instanceof Literal(Token token, Object value)) {
                    var v = ScriptHelpers.toBoolean(value);
                    Token newToken;
                    if (v) {
                        newToken = Token.from(TokenType.FALSE, "FALSE", Boolean.FALSE, token.line(), token.position());
                    } else {
                        newToken = Token.from(TokenType.TRUE, "TRUE", Boolean.TRUE, token.line(), token.position());
                    }
                    return Literal.from(newToken);
                }

                // Optimize multiple NOT operations as they can cancel each other out
                if (right instanceof Unary u && u.operator().type() == TokenType.NOT) {
                    // Basically we can promote the operand of the target canceling the NOT operations
                    // out
                    return u.right();
                }
            }
            break;
            case NEG: {
                // Optimize negation of a numeric constant
                if (right instanceof Literal(Token token, Object value)) {
                    // Should be a number; negate the constant
                    if (token.type() != TokenType.NUMBER) {
                        ScriptException.throwException(token, "Number expected");
                    }
                    var n = -ScriptHelpers.toDouble(value);
                    var newToken = Token.from(TokenType.NUMBER, Double.toString(n), n, token.line(), token.position());
                    return Literal.from(newToken);
                }

                // Optimize multiple NEG operations as they can cancel each other out
                if (right instanceof Unary u && u.operator().type() == TokenType.NEG) {
                    // Basically we can promote the operand of the target canceling the NEG operations
                    // out
                    return u.right();
                }
            }
            break;
        }
        return Unary.from(operator, right);
    }

    Expression compile(String script) {
        var lexer = new Lexer(script);
        var tokens = lexer.getTokens();
        var rpnTokens = new RpnConverter(this.environment).infixToRpn(tokens);
        return this.translate(rpnTokens);
    }
}
