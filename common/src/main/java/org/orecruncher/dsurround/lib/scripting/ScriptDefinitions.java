// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting;

import org.orecruncher.dsurround.lib.scripting.engine.Environment;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Literal;

import java.util.*;
import java.util.function.Consumer;

/**
 * Constants and functions defined in config files, for scripts to use by name: {@code def.APPARITION_CHANCE},
 * {@code def.isHalloween()}. Every name is under {@code def.}, which nothing in code may use, so a definition never
 * replaces a built-in one; a later file's definition of a name replaces an earlier one's (the merging is the
 * loader's). Constants are seen by every script; a function belongs to one kind of script, biome rules
 * ({@link Section#BIOME}, biome traits such as {@code SPOOKY}) or the rest ({@link Section#CONDITIONS},
 * {@code diurnal.isNight()} and the like), and is checked against that kind's names.
 * <p>
 * A function takes no arguments and is an expression of the constants, the names its kind of script has, and the
 * other functions of its kind. Each is compiled once into a function of its own, after the functions it uses; one
 * that uses only constants and pure functions comes out as a constant, and is itself pure, so scripts using it are
 * worked out when they compile. A function that uses an unknown name, one of the other kind, or itself (through others
 * or not), is left out and described, with every function that uses it.
 *
 * @param definitions by name ({@code def.}...), in the order they were defined
 */
public record ScriptDefinitions(Map<String, Definition> definitions) {

    public static final ScriptDefinitions EMPTY = new ScriptDefinitions(Map.of());

    /**
     * What a definition is
     */
    public enum Section {
        CONSTANT("constant"),
        BIOME("biome function"),
        CONDITIONS("conditions function");

        private final String description;

        Section(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    /**
     * A constant or function
     *
     * @param name    with its {@code def.}
     * @param section what it is
     * @param value   a constant's value (a Boolean, Double or String), or a function's expression
     * @param source  where it was defined, for problems
     */
    public record Definition(String name, Section section, Object value, String source) {
    }

    public ScriptDefinitions {
        var copy = new TreeMap<String, Definition>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(definitions);
        definitions = Collections.unmodifiableMap(copy);
    }

    /**
     * Defines the constants and one section's functions in an engine, in place of what was defined before. Each
     * problem is described to {@code problems}, and what it concerns is left out.
     */
    void defineIn(ScriptEngine engine, Section functions, Consumer<String> problems) {
        engine.clearDefinitions();
        var available = new TreeMap<String, Definition>(String.CASE_INSENSITIVE_ORDER);
        for (var definition : this.definitions.values()) {
            if (definition.section() == Section.CONSTANT) {
                engine.define(definition.name(), ConstantVariable.of(definition.value()));
                available.put(definition.name(), definition);
            } else if (definition.section() == functions) {
                available.put(definition.name(), definition);
            }
        }

        // What each function uses; one that can't be read, or uses what isn't there for it, is left out
        var uses = new TreeMap<String, Set<String>>(String.CASE_INSENSITIVE_ORDER);
        var dropped = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var definition : available.values()) {
            if (definition.section() == Section.CONSTANT)
                continue;
            try {
                var used = ScriptEngine.definitionsUsed((String) definition.value());
                for (var name : used) {
                    if (available.containsKey(name))
                        continue;
                    var other = this.definitions.get(name);
                    problems.accept(other == null
                            ? "%s (%s) uses %s, which isn't defined".formatted(definition.name(), definition.source(), name)
                            : "%s (%s) uses %s, a %s, which a %s can't use".formatted(definition.name(), definition.source(), name, other.section(), definition.section()));
                    dropped.add(definition.name());
                    break;
                }
                uses.put(definition.name(), used);
            } catch (ScriptException e) {
                problems.accept("%s (%s): %s".formatted(definition.name(), definition.source(), e.getMessageForLogging((String) definition.value())));
                dropped.add(definition.name());
            }
        }

        // Each after those it uses; a loop is left out whole
        var order = new ArrayList<String>();
        var state = new TreeMap<String, Boolean>(String.CASE_INSENSITIVE_ORDER);
        for (var name : uses.keySet())
            this.visit(name, uses, state, new ArrayDeque<>(), order, dropped, problems);

        for (var name : order) {
            var definition = available.get(name);
            if (dropped.contains(name))
                continue;
            var missing = uses.get(name).stream().filter(dropped::contains).findFirst();
            if (missing.isPresent()) {
                problems.accept("%s (%s) uses %s, which was left out".formatted(name, definition.source(), missing.get()));
                dropped.add(name);
                continue;
            }
            var expression = (String) definition.value();
            try {
                var compiled = engine.compile(expression);
                var builder = new FunctionBuilder(definition.name(), engine::define);
                if (compiled instanceof Literal literal) {
                    var value = literal.value();
                    builder.pure().handler(args -> value);
                } else
                    builder.handler(args -> compiled.eval());
            } catch (ScriptException e) {
                problems.accept("%s (%s): %s".formatted(name, definition.source(), e.getMessageForLogging(expression)));
                dropped.add(name);
            }
        }
    }

    // Depth first, putting each function after those it uses; state is false while a function is being visited
    private void visit(String name, Map<String, Set<String>> uses, Map<String, Boolean> state, Deque<String> path,
                       List<String> order, Set<String> dropped, Consumer<String> problems) {
        var seen = state.get(name);
        if (seen != null) {
            if (!seen) {
                // A loop: from where it starts on the path back to here
                var chain = new ArrayList<String>();
                var iterator = path.descendingIterator();
                boolean inLoop = false;
                while (iterator.hasNext()) {
                    var step = iterator.next();
                    inLoop |= step.equalsIgnoreCase(name);
                    if (inLoop)
                        chain.add(step);
                }
                if (chain.stream().noneMatch(dropped::contains))
                    problems.accept("%s use one another: %s -> %s".formatted(String.join(", ", chain), String.join(" -> ", chain), name));
                dropped.addAll(chain);
            }
            return;
        }
        state.put(name, false);
        path.push(name);
        for (var used : uses.getOrDefault(name, Set.of()))
            if (uses.containsKey(used))
                this.visit(used, uses, state, path, order, dropped, problems);
        path.pop();
        state.put(name, true);
        order.add(name);
    }

    /**
     * Whether a name can be given to a definition in a file, where it is written without its {@code def.}: a letter or
     * underscore, then letters, digits and underscores
     */
    public static boolean isValidName(String name) {
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) && name.charAt(0) < 128 || name.charAt(0) == '_'))
            return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c < 128 && (Character.isLetterOrDigit(c) || c == '_')))
                return false;
        }
        return true;
    }

    /**
     * The full name of one written in a file
     */
    public static String fullName(String name) {
        return Environment.DEF_PREFIX + name;
    }
}
