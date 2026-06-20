package org.orecruncher.dsurround.lib.scripting;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small expression evaluator for Dynamic Surroundings config predicates.
 *
 * <p>The 26.2 port avoids bundling Nashorn because Nashorn brings its own ASM
 * stack. A second ASM stack inside a Fabric mod jar can collide with Mixin,
 * Sodium, Iris, and other pre-launch transformers. The bundled DS data only
 * uses a compact JavaScript-like predicate subset, so this evaluator handles
 * that subset directly.</p>
 */
final class SimpleExpressionEvaluator {

    private final Map<String, Object> variables = new HashMap<>();

    public void put(final String name, final Object value) {
        this.variables.put(name, value);
    }

    public boolean contains(final String name) {
        return this.variables.containsKey(name);
    }

    public Object eval(final String source) {
        var parser = new Parser(source == null || source.isBlank() ? "false" : source);
        var result = parser.parseExpression();
        parser.expectEnd();
        return result;
    }

    public static boolean asBoolean(final Object value) {
        if (value instanceof Boolean b)
            return b;
        if (value instanceof Number n)
            return n.doubleValue() != 0D;
        if (value instanceof CharSequence s) {
            var text = s.toString();
            if ("true".equalsIgnoreCase(text))
                return true;
            if ("false".equalsIgnoreCase(text))
                return false;
            return !text.isBlank();
        }
        return value != null;
    }

    private final class Parser {
        private final String source;
        private int pos;

        private Parser(final String source) {
            this.source = source;
        }

        private Object parseExpression() {
            return parseOr();
        }

        private Object parseOr() {
            var left = parseAnd();
            while (true) {
                if (!match("||"))
                    return left;
                var right = parseAnd();
                left = asBoolean(left) || asBoolean(right);
            }
        }

        private Object parseAnd() {
            var left = parseEquality();
            while (true) {
                if (!match("&&"))
                    return left;
                var right = parseEquality();
                left = asBoolean(left) && asBoolean(right);
            }
        }

        private Object parseEquality() {
            var left = parseRelational();
            while (true) {
                if (match("==")) {
                    var right = parseRelational();
                    left = equalsValue(left, right);
                } else if (match("!=")) {
                    var right = parseRelational();
                    left = !equalsValue(left, right);
                } else {
                    return left;
                }
            }
        }

        private Object parseRelational() {
            var left = parseAdditive();
            while (true) {
                if (match("<=")) {
                    var right = parseAdditive();
                    left = compare(left, right) <= 0;
                } else if (match(">=")) {
                    var right = parseAdditive();
                    left = compare(left, right) >= 0;
                } else if (match("<")) {
                    var right = parseAdditive();
                    left = compare(left, right) < 0;
                } else if (match(">")) {
                    var right = parseAdditive();
                    left = compare(left, right) > 0;
                } else {
                    return left;
                }
            }
        }

        private Object parseAdditive() {
            var left = parseMultiplicative();
            while (true) {
                if (match("+")) {
                    var right = parseMultiplicative();
                    if (left instanceof CharSequence || right instanceof CharSequence)
                        left = String.valueOf(left) + right;
                    else
                        left = asNumber(left) + asNumber(right);
                } else if (match("-")) {
                    var right = parseMultiplicative();
                    left = asNumber(left) - asNumber(right);
                } else {
                    return left;
                }
            }
        }

        private Object parseMultiplicative() {
            var left = parseUnary();
            while (true) {
                if (match("*")) {
                    var right = parseUnary();
                    left = asNumber(left) * asNumber(right);
                } else if (match("/")) {
                    var right = parseUnary();
                    left = asNumber(left) / asNumber(right);
                } else if (match("%")) {
                    var right = parseUnary();
                    left = asNumber(left) % asNumber(right);
                } else {
                    return left;
                }
            }
        }

        private Object parseUnary() {
            skipWhitespace();
            if (match("!"))
                return !asBoolean(parseUnary());
            if (match("+"))
                return asNumber(parseUnary());
            if (match("-"))
                return -asNumber(parseUnary());
            return parsePrimary();
        }

        private Object parsePrimary() {
            skipWhitespace();
            if (match("(")) {
                var value = parseExpression();
                expect(")");
                return value;
            }

            char ch = peek();
            if (ch == '\'' || ch == '"')
                return parseString();
            if (isNumberStart(ch))
                return parseNumber();
            if (isIdentifierStart(ch))
                return parseIdentifierExpression();

            throw error("Expected expression");
        }

        private Object parseIdentifierExpression() {
            var name = parseIdentifier();
            Object value = switch (name) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> null;
                default -> variables.getOrDefault(name, Boolean.FALSE);
            };

            while (true) {
                skipWhitespace();
                if (!match("."))
                    return value;

                var member = parseIdentifier();
                skipWhitespace();
                if (match("(")) {
                    var args = parseArguments();
                    value = invoke(value, member, args);
                } else {
                    value = getProperty(value, member);
                }
            }
        }

        private List<Object> parseArguments() {
            var args = new ArrayList<Object>();
            skipWhitespace();
            if (match(")"))
                return args;
            do {
                args.add(parseExpression());
                skipWhitespace();
            } while (match(","));
            expect(")");
            return args;
        }

        private String parseIdentifier() {
            skipWhitespace();
            if (!isIdentifierStart(peek()))
                throw error("Expected identifier");
            int start = this.pos++;
            while (isIdentifierPart(peek()))
                this.pos++;
            return this.source.substring(start, this.pos);
        }

        private String parseString() {
            char quote = this.source.charAt(this.pos++);
            var sb = new StringBuilder();
            while (!isEnd()) {
                char ch = this.source.charAt(this.pos++);
                if (ch == quote)
                    return sb.toString();
                if (ch == '\\' && !isEnd()) {
                    char escaped = this.source.charAt(this.pos++);
                    sb.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case '\\' -> '\\';
                        case '\'' -> '\'';
                        case '"' -> '"';
                        default -> escaped;
                    });
                } else {
                    sb.append(ch);
                }
            }
            throw error("Unterminated string literal");
        }

        private Number parseNumber() {
            int start = this.pos;
            if (peek() == '-')
                this.pos++;
            while (Character.isDigit(peek()))
                this.pos++;
            if (peek() == '.') {
                this.pos++;
                while (Character.isDigit(peek()))
                    this.pos++;
            }
            if (peek() == 'e' || peek() == 'E') {
                this.pos++;
                if (peek() == '+' || peek() == '-')
                    this.pos++;
                while (Character.isDigit(peek()))
                    this.pos++;
            }
            return Double.parseDouble(this.source.substring(start, this.pos));
        }

        private void expect(final String token) {
            if (!match(token))
                throw error("Expected '" + token + "'");
        }

        private void expectEnd() {
            skipWhitespace();
            if (match(";"))
                skipWhitespace();
            if (!isEnd())
                throw error("Unexpected token");
        }

        private boolean match(final String token) {
            skipWhitespace();
            if (this.source.startsWith(token, this.pos)) {
                this.pos += token.length();
                return true;
            }
            return false;
        }

        private char peek() {
            return isEnd() ? '\0' : this.source.charAt(this.pos);
        }

        private boolean isEnd() {
            return this.pos >= this.source.length();
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(this.source.charAt(this.pos)))
                this.pos++;
        }

        private IllegalArgumentException error(final String message) {
            return new IllegalArgumentException(message + " at index " + this.pos + " in [" + this.source + "]");
        }
    }

    private static boolean isIdentifierStart(final char ch) {
        return Character.isLetter(ch) || ch == '_' || ch == '$';
    }

    private static boolean isIdentifierPart(final char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    private static boolean isNumberStart(final char ch) {
        return Character.isDigit(ch) || ch == '.';
    }

    private static Object getProperty(final Object target, final String property) {
        if (target == null)
            return Boolean.FALSE;
        if (target instanceof Map<?, ?> map)
            return map.containsKey(property) ? map.get(property) : Boolean.FALSE;

        var capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (var name : List.of(property, "get" + capitalized, "is" + capitalized)) {
            try {
                var method = target.getClass().getMethod(name);
                if (Modifier.isPublic(method.getModifiers()))
                    return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                throw new IllegalArgumentException("Unable to read property '" + property + "' from " + target.getClass().getName(), t);
            }
        }
        return Boolean.FALSE;
    }

    private static Object invoke(final Object target, final String methodName, final List<Object> args) {
        if (target == null)
            return Boolean.FALSE;

        Method fallback = null;
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || !Modifier.isPublic(method.getModifiers()))
                continue;

            if (method.isVarArgs()) {
                if (args.size() < method.getParameterCount() - 1)
                    continue;
            } else if (method.getParameterCount() != args.size()) {
                continue;
            }

            fallback = method;
            try {
                return method.invoke(target, adaptArguments(method, args));
            } catch (IllegalArgumentException ignored) {
                // Keep looking for another overload that can accept the values.
            } catch (Throwable t) {
                throw new IllegalArgumentException("Unable to call method '" + methodName + "' on " + target.getClass().getName(), t);
            }
        }

        if (fallback != null)
            throw new IllegalArgumentException("Unable to adapt arguments for method '" + methodName + "' on " + target.getClass().getName());
        return Boolean.FALSE;
    }

    private static Object[] adaptArguments(final Method method, final List<Object> args) {
        var parameterTypes = method.getParameterTypes();
        var result = new Object[parameterTypes.length];

        if (!method.isVarArgs()) {
            for (int i = 0; i < parameterTypes.length; i++)
                result[i] = adaptValue(args.get(i), parameterTypes[i]);
            return result;
        }

        int fixedCount = parameterTypes.length - 1;
        for (int i = 0; i < fixedCount; i++)
            result[i] = adaptValue(args.get(i), parameterTypes[i]);

        var arrayType = parameterTypes[parameterTypes.length - 1].componentType();
        var varargArray = Array.newInstance(arrayType, args.size() - fixedCount);
        for (int i = fixedCount; i < args.size(); i++)
            Array.set(varargArray, i - fixedCount, adaptValue(args.get(i), arrayType));
        result[result.length - 1] = varargArray;
        return result;
    }

    private static Object adaptValue(final Object value, final Class<?> desiredType) {
        if (value == null)
            return desiredType.isPrimitive() ? primitiveDefault(desiredType) : null;
        if (desiredType.isInstance(value))
            return value;
        if (desiredType == String.class)
            return String.valueOf(value);
        if (desiredType == boolean.class || desiredType == Boolean.class)
            return asBoolean(value);
        if (desiredType == int.class || desiredType == Integer.class)
            return (int) asNumber(value);
        if (desiredType == long.class || desiredType == Long.class)
            return (long) asNumber(value);
        if (desiredType == float.class || desiredType == Float.class)
            return (float) asNumber(value);
        if (desiredType == double.class || desiredType == Double.class)
            return asNumber(value);
        if (desiredType == Object.class)
            return value;
        return value;
    }

    private static Object primitiveDefault(final Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == char.class)
            return '\0';
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == float.class)
            return 0F;
        if (type == double.class)
            return 0D;
        return null;
    }

    private static boolean equalsValue(final Object left, final Object right) {
        if (left instanceof Number || right instanceof Number)
            return Double.compare(asNumber(left), asNumber(right)) == 0;
        if (left instanceof Boolean || right instanceof Boolean)
            return asBoolean(left) == asBoolean(right);
        return Objects.equals(left, right) || String.valueOf(left).equals(String.valueOf(right));
    }

    private static int compare(final Object left, final Object right) {
        if (left instanceof Number || right instanceof Number)
            return Double.compare(asNumber(left), asNumber(right));
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static double asNumber(final Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        if (value instanceof Boolean b)
            return b ? 1D : 0D;
        if (value == null)
            return 0D;
        return Double.parseDouble(String.valueOf(value));
    }
}
