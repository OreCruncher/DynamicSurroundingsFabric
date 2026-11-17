// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions.Definition;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions.Section;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Literal;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for constants and functions defined in config files: named under def., checked against their kind of script,
 * compiled after what they use, loops and unknown names left out and described.
 */
public class ScriptDefinitionsTests {

    private final List<String> problems = new ArrayList<>();
    private final AtomicInteger hour = new AtomicInteger(22);
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        this.problems.clear();
        this.context = new ExecutionContext("test", new RecordingLog());
        this.context.add(new VariableSet("clock") {
            @Override
            public void configure(IConfigureDefinition config) {
                config.property(this.id("hour"), ScriptDefinitionsTests.this.hour::get);
            }
        });
    }

    private static ScriptDefinitions definitions(Definition... definitions) {
        var map = new LinkedHashMap<String, Definition>();
        for (var definition : definitions)
            map.put(definition.name(), definition);
        return new ScriptDefinitions(map);
    }

    private static Definition constant(String name, Object value) {
        return new Definition("def." + name, Section.CONSTANT, value, "test");
    }

    private static Definition condition(String name, String expression) {
        return new Definition("def." + name, Section.CONDITIONS, expression, "test");
    }

    private static Definition biome(String name, String expression) {
        return new Definition("def." + name, Section.BIOME, expression, "test");
    }

    private void define(ScriptDefinitions definitions) {
        this.context.define(definitions, Section.CONDITIONS, this.problems::add);
    }

    private Object eval(String script) {
        return this.context.eval(new Script(script)).orElse(null);
    }

    // Whether a script fails to compile, its name unknown
    private void assertUnknown(String script) {
        var result = String.valueOf(this.eval(script));
        assertTrue(result.contains("Unable to locate") || result.contains("undefined function"), () -> script + " gave " + result);
    }

    @Test
    void constantsAndFunctionsAreUsedByName() {
        this.define(definitions(constant("CHANCE", 0.1), constant("NAME", "ghost"), constant("ON", true),
                condition("isNight", "clock.hour() >= 20"), condition("apparitionChance", "def.isNight() ? def.CHANCE * 3 : def.CHANCE")));
        assertEquals(List.of(), this.problems);
        assertEquals(0.1D, this.eval("def.CHANCE"));
        assertEquals("ghost", this.eval("def.NAME"));
        assertEquals(true, this.eval("def.ON && def.isNight()"));
        assertEquals(0.3D, (Double) this.eval("def.apparitionChance()"), 1e-9D, "a function using another");
        assertEquals(0.1D, (Double) this.eval("DEF.APPARITIONchance() - Def.chance * 2"), 1e-9D, "names don't care about case");
    }

    @Test
    void namesDifferingOnlyInCaseAreOne() {
        // As every script name: the later replaces the earlier, a constant by a function here
        var both = definitions(constant("CHANCE", 0.1), condition("chance", "0.5"));
        assertEquals(1, both.definitions().size());
        this.define(both);
        assertEquals(0.5D, this.eval("def.Chance()"));
    }

    @Test
    void aFunctionIsWorkedOutEachTimeItIsUsed() {
        this.define(definitions(condition("isNight", "clock.hour() >= 20")));
        assertEquals(true, this.eval("def.isNight()"));
        this.hour.set(9);
        assertEquals(false, this.eval("def.isNight()"));
    }

    @Test
    void aFunctionOfConstantsIsWorkedOutWhenScriptsCompile() {
        var engine = new ScriptEngine();
        definitions(constant("BASE", 2D), condition("twice", "def.BASE * 2"), condition("more", "def.twice() + 1"))
                .defineIn(engine, Section.CONDITIONS, this.problems::add);
        assertEquals(List.of(), this.problems);
        var compiled = engine.compile("def.more() * 10");
        assertInstanceOf(Literal.class, compiled, "constant all the way down");
        assertEquals(50D, compiled.eval());
    }

    @Test
    void aFunctionUsingTheClockIsNotFolded() {
        var engine = new ScriptEngine();
        engine.defineFunction(new ScriptFunction("clock.hour", List.of(), 0, null, false, false, args -> 21D));
        definitions(condition("isNight", "clock.hour() >= 20")).defineIn(engine, Section.CONDITIONS, this.problems::add);
        assertFalse(engine.compile("def.isNight()") instanceof Literal);
    }

    @Test
    void aLoopIsLeftOutWholeAndTheRestStays() {
        this.define(definitions(condition("a", "def.b()"), condition("b", "def.c() || def.a()"), condition("c", "def.b()"),
                condition("usesLoop", "!def.a()"), condition("fine", "1 + 1")));
        assertEquals(2D, this.eval("def.fine()"));
        var loop = this.problems.stream().filter(p -> p.contains("use one another")).toList();
        assertEquals(1, loop.size(), this.problems.toString());
        assertTrue(loop.getFirst().contains("def.a -> def.b") || loop.getFirst().contains("def.b -> def.c"), loop.getFirst());
        assertTrue(this.problems.stream().anyMatch(p -> p.startsWith("def.usesLoop (test) uses def.a, which was left out")), this.problems.toString());
        for (var gone : List.of("def.a()", "def.b()", "def.c()", "def.usesLoop()"))
            this.assertUnknown(gone);
    }

    @Test
    void aFunctionUsingItselfIsALoop() {
        this.define(definitions(condition("self", "def.self()")));
        assertEquals(List.of("def.self use one another: def.self -> def.self"), this.problems);
    }

    @Test
    void unknownNamesAndTheOtherKindAreDescribed() {
        this.define(definitions(condition("typo", "def.isHalowen()"), biome("isHaunted", "SPOOKY"),
                condition("crossed", "def.isHaunted()"), condition("bad", "clock.hour( >= 20")));
        assertTrue(this.problems.contains("def.typo (test) uses def.isHalowen, which isn't defined"), this.problems.toString());
        assertTrue(this.problems.contains("def.crossed (test) uses def.isHaunted, a biome function, which a conditions function can't use"), this.problems.toString());
        assertTrue(this.problems.stream().anyMatch(p -> p.startsWith("def.bad (test): ")), this.problems.toString());
        this.assertUnknown("def.isHaunted()");
    }

    @Test
    void definingAgainReplacesEverything() {
        this.define(definitions(constant("OLD", 1D), condition("former", "true")));
        assertEquals(1D, this.eval("def.OLD"));
        this.define(definitions(constant("NEW", 2D)));
        assertEquals(2D, this.eval("def.NEW"));
        this.assertUnknown("def.OLD");
        this.assertUnknown("def.former()");
    }

    @Test
    void codeCantUseTheDefinitionsNames() {
        var engine = new ScriptEngine();
        var e = assertThrows(ScriptException.class, () -> engine.property("def.sneaky", () -> true));
        assertTrue(e.getMessage().contains("kept for config definitions"), e.getMessage());
        assertThrows(ScriptException.class, () -> engine.defineVariable("DEF.sneaky", ConstantVariable.TRUE), "whatever its case");
        assertThrows(ScriptException.class, () -> engine.define("clock.hour", ConstantVariable.TRUE), "and definitions can't use code's");
    }

    @Test
    void aDefinitionCantReplaceCode() {
        // A definition's name always starts def., which code never uses: a built-in name stays what code made it
        this.define(definitions(condition("hour", "99")));
        assertEquals(22, this.eval("clock.hour()"));
        assertEquals(99D, this.eval("def.hour()"));
    }

    @Test
    void garbledDefinitionsNeverEscape() {
        // Expressions as a hand-edited file breaks them: characters dropped, doubled or changed, names mistyped, and
        // functions that use one another in every way, loops included
        var random = Randomizer.create(0x5C121D7L);
        var pieces = List.of("def.a()", "def.b()", "def.c()", "def.K", "clock.hour()", " && ", " || ", " ? ", " : ", "!", "(", ")",
                " + ", " * ", "1", "0.5", "'x'", "true", " >= ", "def.", "()", ",");
        var names = List.of("a", "b", "c", "K");
        for (int round = 0; round < 400; round++) {
            var map = new LinkedHashMap<String, Definition>();
            map.put("def.K", constant("K", 2D));
            for (var name : names.subList(0, 3)) {
                var text = new StringBuilder();
                int parts = 1 + random.nextInt(6);
                for (int i = 0; i < parts; i++)
                    text.append(pieces.get(random.nextInt(pieces.size())));
                map.put("def." + name, condition(name, text.toString()));
            }
            var definitions = new ScriptDefinitions(map);
            var found = new ArrayList<String>();
            long started = System.nanoTime();
            assertDoesNotThrow(() -> this.context.define(definitions, Section.CONDITIONS, found::add), map::toString);
            for (var name : names)
                assertDoesNotThrow(() -> this.eval("def." + name + (name.equals("K") ? "" : "()")), map::toString);
            assertTrue(System.nanoTime() - started < 1_000_000_000L, map::toString);
        }
    }

    @Test
    void theDefinitionsUsedAreFoundInTheText() {
        assertEquals(Set.of("def.a", "def.B"), ScriptEngine.definitionsUsed("def.a() && clock.hour() > def.B || 'def.c'"));
    }

    @Test
    void namesAreLettersDigitsAndUnderscores() {
        assertTrue(ScriptDefinitions.isValidName("isHalloween"));
        assertTrue(ScriptDefinitions.isValidName("_CHANCE_2"));
        assertFalse(ScriptDefinitions.isValidName(""));
        assertFalse(ScriptDefinitions.isValidName("2fast"));
        assertFalse(ScriptDefinitions.isValidName("def.isHalloween"), "written without def.");
        assertFalse(ScriptDefinitions.isValidName("has space"));
        assertFalse(ScriptDefinitions.isValidName("café"));
    }
}
