package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.IScriptFunction;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;

import java.util.List;

public abstract class Expression {

    final Environment environment;

    Expression(Environment environment) {
        this.environment = environment;
    }

    static final class Binary extends Expression {

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
                    return (l, r) -> this.toDouble(l.eval(), true) > this.toDouble(r.eval(), false);
                case GREATER_EQUAL:
                    return (l, r) -> this.toDouble(l.eval(), true) >= this.toDouble(r.eval(), false);
                case LESS:
                    return (l, r) -> this.toDouble(l.eval(), true) < this.toDouble(r.eval(), false);
                case LESS_EQUAL:
                    return (l, r) -> this.toDouble(l.eval(), true) <= this.toDouble(r.eval(), false);
                case MINUS:
                    return (l, r) -> this.toDouble(l.eval(), true) - this.toDouble(r.eval(), false);
                case PLUS:
                    return (l, r) -> {
                        var leftValue = l.eval();
                        var rightValue = r.eval();
                        if (leftValue instanceof Number && rightValue instanceof Number) {
                            return ((Number) leftValue).doubleValue() + ((Number) rightValue).doubleValue();
                        }

                        if (leftValue instanceof String || rightValue instanceof String) {
                            return leftValue.toString() + rightValue.toString();
                        }
                        ScriptException.throwException(this.operator, "Incompatible operands for operator '%s'".formatted(operator.lexeme()));
                        return null;
                    };
                case SLASH:
                    return (l, r) -> this.toDouble(l.eval(), true) / this.toDouble(r.eval(), false);
                case STAR:
                    return (l, r) -> this.toDouble(l.eval(), true) * this.toDouble(r.eval(), false);
                case CONDITIONAL_OR:
                    return (l, r) -> this.toBoolean(l.eval(), true) || this.toBoolean(r.eval(), false);
                case CONDITIONAL_AND:
                    return (l, r) -> this.toBoolean(l.eval(), true) && this.toBoolean(r.eval(), false);
            }

            ScriptException.throwException(this.operator, "Unknown operator type '%s'".formatted(this.operator.lexeme()));
            return null;
        }

        @Override
        public Object eval() {
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

        private double toDouble(Object value, boolean leftOperand) {
            try {
                return ScriptHelpers.toDouble(value);
            } catch(Throwable ignored){}
            ScriptException.throwException(this.operator, "%s operand must be a number or value that converts to a number".formatted(leftOperand ? "Left" : "Right"));
            return 0D;
        }

        private boolean toBoolean(Object value, boolean leftOperand) {
            try {
                return ScriptHelpers.toBoolean(value);
            } catch (Throwable ignored) {}
            ScriptException.throwException(this.operator, "%s operand must be a boolean or value that converts to a boolean".formatted(leftOperand ? "Left" : "Right"));
            return false;
        }
    }

    static final class Call extends Expression {

        final static Expression[] NO_ARGUMENTS = new Expression[0];
        final static Object[] NO_VALUES = new Object[0];

        final Token token;
        final IScriptFunction function;
        final Expression[] arguments;
        final Object[] values;

        Call(Environment environment, Token token, List<Expression> arguments) {
            super(environment);
            this.token = token;
            if (arguments.isEmpty()) {
                this.arguments = NO_ARGUMENTS;
                this.values = NO_VALUES;
            } else {
                this.arguments = arguments.toArray(new Expression[0]);
                this.values = new Object[this.arguments.length];
            }
            this.function = this.environment.getFunctionHandler(token);
        }

        @Override
        public Object eval() {
            // Get the data for the call
            if (this.arguments.length > 0) {
                for (int i = 0; i < this.arguments.length; i++) {
                    this.values[i] = this.arguments[i].eval();
                }
            }
            return this.function.evaluate(this.values);
        }
    }

    static final class Literal extends Expression {

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
        public Object eval() {
            return this.value;
        }
    }

    static final class Unary extends Expression {

        final Token operator;
        final Expression right;

        Unary(Environment environment, Token operator, Expression right) {
            super(environment);
            this.operator = operator;
            this.right = right;
        }

        @Override
        public Object eval() {
            var value = this.right.eval();
            return !((Boolean)value);
        }
    }

    static final class Variable extends Expression {

        final Token name;
        final IScriptVariable variable;

        Variable(Environment environment, Token name) {
            super(environment);
            this.name = name;
            this.variable = this.environment.getVariable(this.name);
        }

        @Override
        public Object eval() {
            return this.variable.getValue();
        }
    }

    public abstract Object eval();

    static boolean isLiteral(Token token) {
        var type = token.type();
        return type == TokenType.NUMBER || type == TokenType.STRING || type == TokenType.TRUE || type == TokenType.FALSE;
    }

    static boolean isIdentifier(Token token) {
        return token.type() == TokenType.IDENTIFIER;
    }

    @FunctionalInterface
    interface IOperationHandler {
        Object eval(Expression expr1, Expression expr2);
    }
}
