package org.orecruncher.dsurround.lib.scripting.engine;

import org.orecruncher.dsurround.lib.scripting.ConstantVariable;
import org.orecruncher.dsurround.lib.scripting.engine.expression.*;

import java.util.*;

record Compiler(Environment environment) {

    /**
     * Builds the expression tree. The RPN has already been validated by RpnConverter, so every operator and
     * function is guaranteed its operands and exactly one expression remains at the end.
     */
    Expression translate(List<RpnConverter.RpnToken> tokens) {
        Deque<Expression> stack = new ArrayDeque<>();
        // Parallel to the expression stack: the first token (in source order) of each sub-expression. Used to
        // report problems with a function argument where that argument starts in the script.
        Deque<Token> starts = new ArrayDeque<>();

        // Iterate through the tokens generating a syntax tree
        for (var token : tokens) {
            if (token.isFunction()) {
                List<Expression> children = new ArrayList<>();
                List<Token> childStarts = new ArrayList<>();
                // Pop N arguments off the stack (popped in reverse order)
                for (int i = 0; i < token.argCount(); i++) {
                    children.add(stack.pop());
                    childStarts.add(starts.pop());
                }

                // Reverse to restore original argument ordering
                Collections.reverse(children);
                Collections.reverse(childStarts);

                stack.push(this.createCall(token.token(), children, childStarts));
                starts.push(token.token());
            } else if (token.token().type().isBinaryOperator()) {
                Expression right = stack.pop();
                Expression left = stack.pop();
                Expression result = this.optimizeBinaryOperation(left, token.token(), right);

                stack.push(result);
                // The result starts where the left operand starts
                starts.pop();
            } else if (token.token().type().isUnaryOperator()) {
                Expression right = stack.pop();
                var result = this.optimizeUnaryOperation(token.token(), right);

                stack.push(result);
                // Prefix operator: the result starts at the operator
                starts.pop();
                starts.push(token.token());
            } else if (TokenType.isIdentifier(token.token().type())) {
                stack.push(this.createVariable(token.token()));
                starts.push(token.token());
            } else if (TokenType.isLiteral(token.token().type())) {
                stack.push(Literal.from(token.token()));
                starts.push(token.token());
            } else {
                // The lexer produces no other token types; this guards against adding one without handling it here
                ScriptException.throwException(token.token(), "Unexpected token '%s'".formatted(token.token().lexeme()));
            }
        }

        return stack.pop();
    }

    /**
     * A constant, such as math.pi, compiles to a literal so that expressions using it can be folded.
     */
    private Expression createVariable(Token token) {
        var variable = Variable.from(this.environment, token);
        if (variable.variable() instanceof ConstantVariable<?> constant) {
            var literal = literalOf(constant.value(), token);
            if (literal != null)
                return literal;
        }
        return variable;
    }

    /**
     * Creates a function call. Constant arguments are converted to their parameter types now, so an invalid
     * constant is reported as a compile error at the argument. A pure function whose arguments are all constant
     * is evaluated now and replaced by its result.
     */
    private Expression createCall(Token token, List<Expression> arguments, List<Token> argumentStarts) {
        var function = this.environment.getFunction(token);
        int count = arguments.size();
        var constants = new Object[count];
        var isConstant = new boolean[count];
        boolean allConstant = true;

        for (int i = 0; i < count; i++) {
            if (arguments.get(i) instanceof Literal literal) {
                constants[i] = Call.convertArgument(function, i, literal.value(), argumentStarts.get(i));
                isConstant[i] = true;
            } else {
                allConstant = false;
            }
        }

        var call = new Call(token, function, arguments.toArray(new Expression[0]), argumentStarts.toArray(new Token[0]), constants, isConstant);

        if (function.pure() && allConstant) {
            // Would produce the same result (or the same error) on every evaluation
            var folded = literalOf(call.eval(), token);
            if (folded != null)
                return folded;
        }

        if (function.compiler() != null) {
            var specialized = function.compiler().compile(call);
            if (specialized != null)
                return specialized;
        }

        return call;
    }

    private Expression optimizeBinaryOperation(Expression left, Token operator, Expression right) {
        // If both operands are literals evaluate now. This uses the exact same code path as runtime evaluation,
        // so folded results cannot differ from what the script would produce. Errors (such as 'abc' * 2) are
        // reported at compile time since they would fail on every evaluation.
        if (left instanceof Literal && right instanceof Literal) {
            var folded = literalOf(Binary.from(left, operator, right).eval(), operator);
            if (folded != null)
                return folded;
        }

        switch (operator.type()) {
            case CONDITIONAL_OR: {
                // Optimize for literal true
                if (left instanceof Literal(Token token, Object value) && ScriptHelpers.toBoolean(value)) {
                    return literalOf(Boolean.TRUE, token);
                }

                if (right instanceof Literal(Token token, Object value) && ScriptHelpers.toBoolean(value)) {
                    return literalOf(Boolean.TRUE, token);
                }
            }
            break;
            case CONDITIONAL_AND: {
                // Optimize for literal false
                if (left instanceof Literal(Token token, Object value) && !ScriptHelpers.toBoolean(value)) {
                    return literalOf(Boolean.FALSE, token);
                }

                if (right instanceof Literal(Token token, Object value) && !ScriptHelpers.toBoolean(value)) {
                    return literalOf(Boolean.FALSE, token);
                }
            }
            break;
            default:
                break;
        }
        return Binary.from(left, operator, right);
    }

    private Expression optimizeUnaryOperation(Token operator, Expression right) {

        // Fold operations on constants using the runtime code path
        // (Errors such as -true are reported at compile time since they would fail on every evaluation.)
        if (right instanceof Literal literal) {
            var value = Unary.from(operator, right).eval();
            var folded = literalOf(value, literal.token());
            if (folded != null)
                return folded;
        }

        // Double negation cancels out, but only when the inner operand already has the type the operator would
        // coerce to. Otherwise, !!5 would become 5 instead of true, and --'5' would become '5' instead of 5.
        if (right instanceof Unary u && u.operator().type() == operator.type()) {
            switch (operator.type()) {
                case NOT -> {
                    if (yieldsBoolean(u.right()))
                        return u.right();
                }
                case NEG -> {
                    if (yieldsNumber(u.right()))
                        return u.right();
                }
                default -> {
                }
            }
        }

        return Unary.from(operator, right);
    }

    /**
     * Determines if the expression is statically known to produce a Boolean.
     */
    private static boolean yieldsBoolean(Expression expression) {
        if (expression instanceof Literal l)
            return l.value() instanceof Boolean;
        if (expression instanceof Unary u)
            return u.operator().type() == TokenType.NOT;
        if (expression instanceof Binary b) {
            return switch (b.operator().type()) {
                case EQUAL_EQUAL, NOT_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, CONDITIONAL_AND, CONDITIONAL_OR -> true;
                default -> false;
            };
        }
        return false;
    }

    /**
     * Determines if the expression is statically known to produce a Double.
     */
    private static boolean yieldsNumber(Expression expression) {
        if (expression instanceof Literal l)
            return l.value() instanceof Double;
        if (expression instanceof Unary u)
            return u.operator().type() == TokenType.NEG;
        if (expression instanceof Binary b) {
            return switch (b.operator().type()) {
                case MINUS, STAR, SLASH -> true;
                // PLUS produces a string if either side is a string
                case PLUS -> yieldsNumber(b.left()) && yieldsNumber(b.right());
                default -> false;
            };
        }
        return false;
    }

    /**
     * Creates a literal for a folded value, or null if the value has no literal representation.
     */
    private static Literal literalOf(Object value, Token at) {
        if (value instanceof Boolean b) {
            return Literal.from(Token.derive(b ? TokenType.TRUE : TokenType.FALSE, b ? "TRUE" : "FALSE", b, at));
        }
        if (value instanceof Number n) {
            var d = n.doubleValue();
            return Literal.from(Token.derive(TokenType.NUMBER, ScriptHelpers.toStringValue(d), d, at));
        }
        if (value instanceof String s) {
            return Literal.from(Token.derive(TokenType.STRING, "'" + s + "'", s, at));
        }
        return null;
    }

    Expression compile(String script) {
        var lexer = new Lexer(script);
        var tokens = lexer.getTokens();
        var rpnTokens = new RpnConverter(this.environment).infixToRpn(tokens);
        return this.translate(rpnTokens);
    }
}
