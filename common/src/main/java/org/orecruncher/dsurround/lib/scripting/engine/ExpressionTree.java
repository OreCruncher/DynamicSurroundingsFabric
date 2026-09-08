package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.Optional;

public class ExpressionTree {

    final Expression expression;

    ExpressionTree(Expression expression) {
        this.expression = expression;
    }

    /**
     * Evaluates the expression and returns a boolean true/false based on the result.
     * @return true if the expression evaluated true; false otherwise
     */
    public boolean check() {
        final Optional<Object> result = this.eval();
        if (result.isPresent())
            return "true".equalsIgnoreCase(result.toString());
        return false;
    }

    /**
     * Evaluates the expression and returns it's results.
     * @return Result of evaluation
     */
    public Optional<Object> eval() {
        try {
            return Optional.of(this.expression.eval());
        } catch(Throwable t) {
            return Optional.of("ERROR? " + t.getMessage());
        }
    }
}
