package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.IScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.ArrayList;
import java.util.List;

abstract class Expression {

    final Environment environment;

    Expression(Environment environment) {
        this.environment = environment;
    }

    static class Binary extends Expression {

        final Expression left;
        final Token operator;
        final Expression right;
        final IOperationHandler function;

        Binary(Environment environment, Expression left, Token operator, Expression right) {
            super(environment);
            this.left = left;
            this.operator = operator;
            this.right = right;
            this.function = this.getFunction();
        }

        private IOperationHandler getFunction() {
            switch (this.operator.type()) {
                case NOT_EQUAL:
                    return (l, r) -> !this.isEqual(l, r);
                case EQUAL_EQUAL:
                    return this::isEqual;
                case GREATER:
                    return (l, r) -> {
                        var leftValue = this.left.eval();
                        var rightValue = this.right.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() > ((Number) rightValue).doubleValue();
                    };
                case GREATER_EQUAL:
                    return (l, r) -> {
                        var leftValue = this.left.eval();
                        var rightValue = this.right.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() >= ((Number) rightValue).doubleValue();
                    };
                case LESS:
                    return (l, r) -> {
                        var leftValue = this.left.eval();
                        var rightValue = this.right.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() < ((Number) rightValue).doubleValue();
                    };
                case LESS_EQUAL:
                    return (l, r) -> {
                        var leftValue = this.left.eval();
                        var rightValue = this.right.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() <= ((Number) rightValue).doubleValue();
                    };
                case MINUS:
                    return (l, r) -> {
                        var leftValue = this.left.eval();
                        var rightValue = this.right.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() - ((Number) rightValue).doubleValue();
                    };
                case PLUS:
                    return (l, r) -> {
                        var leftValue = l.eval();
                        var rightValue = r.eval();
                        if (leftValue instanceof Number && rightValue instanceof Number) {
                            return ((Number) leftValue).doubleValue() + ((Number) rightValue).doubleValue();
                        }

                        // Assume some sort of disjoint types. Convert to strings and
                        // concatenate.
                        return leftValue.toString() + rightValue.toString();
                    };
                case SLASH:
                    return (l, r) -> {
                        var leftValue = l.eval();
                        var rightValue = r.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() / ((Number) rightValue).doubleValue();
                    };
                case STAR:
                    return (l, r) -> {
                        var leftValue = l.eval();
                        var rightValue = r.eval();
                        this.checkNumberOperands(this.operator, leftValue, rightValue);
                        return ((Number) leftValue).doubleValue() * ((Number) rightValue).doubleValue();
                    };
                case CONDITIONAL_OR:
                    return (l, r) -> (Boolean) l.eval() || (Boolean) r.eval();
                case CONDITIONAL_AND:
                    return (l, r) -> (Boolean) l.eval() && (Boolean) r.eval();
            }

            ScriptException.error(this.operator, "Unknown operator type '%s'".formatted(this.operator.lexeme()));
            return null;
        }

        @Override
        Object eval() {
            return this.function.eval(this.left, this.right);
        }

        private boolean isEqual(Object a, Object b) {
            // nil is only equal to nil.
            if (a == null && b == null)
                return true;
            if (a == null)
                return false;
            return a.equals(b);
        }

        private void checkNumberOperands(Token operator, Object left, Object right) {
            if (left instanceof Number && right instanceof Number)
                return;
            ScriptException.error(operator, "Operands must be numbers");
        }
    }

    static class Call extends Expression {

        final Token token;
        final List<Expression> arguments;
        final List<Object> values;
        final IScriptFunction function;

        Call(Environment environment, Token token, List<Expression> arguments) {
            super(environment);
            this.token = token;
            if (arguments.isEmpty()) {
                this.arguments = List.of();
                this.values = List.of();
            } else {
                this.arguments = arguments;
                this.values = new ArrayList<>(arguments.size());
            }
            this.function = this.environment.getFunctionHandler(token);
        }

        @Override
        Object eval() {
            // Get the data for the call
            if (!this.arguments.isEmpty()) {
                this.values.clear();
                for (Expression argument : this.arguments) {
                    this.values.add(argument.eval());
                }
            }
            return this.function.evaluate(this.values);
        }
    }

    static class Literal extends Expression {

        final Token token;
        final Object value;

        Literal(Environment environment, Token token) {
            super(environment);
            this.token = token;
            if (this.token.type() == TokenType.TRUE)
                this.value = Boolean.TRUE;
            else if (this.token.type() == TokenType.FALSE)
                this.value = Boolean.FALSE;
            else
                this.value = token.literal();
        }

        Literal(Environment environment, String value) {
            super(environment);
            this.token = null;
            this.value = value;
        }

        @Override
        Object eval() {
            return this.value;
        }
    }

    static class Unary extends Expression {

        final Token operator;
        final Expression right;

        Unary(Environment environment, Token operator, Expression right) {
            super(environment);
            this.operator = operator;
            this.right = right;
        }

        @Override
        Object eval() {
            var value = this.right.eval();
            return !((Boolean)value);
        }
    }

    static class Variable extends Expression {

        final Token name;
        final IScriptVariable variable;

        Variable(Environment environment, Token name) {
            super(environment);
            this.name = name;
            this.variable = this.environment.getVariable(this.name);
        }

        @Override
        Object eval() {
            return this.variable.getValue();
        }
    }

    abstract Object eval();

    @FunctionalInterface
    interface IOperationHandler {
        Object eval(Expression expr1, Expression expr2);
    }
}
