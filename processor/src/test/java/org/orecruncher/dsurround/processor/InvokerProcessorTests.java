package org.orecruncher.dsurround.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.*;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compiles small sources with the processor attached, then checks the errors reported or loads and runs the
 * generated invokers. The annotation and {@code EventingFactory.handlerFailed} are stubs here, so these tests don't
 * depend on the mod.
 */
public class InvokerProcessorTests {

    @TempDir
    Path output;

    // ---- Harness ---------------------------------------------------------------------------------------------

    private static final String ANNOTATION_STUB = """
            package org.orecruncher.dsurround.lib.events;
            public @interface GenerateInvoker {}
            """;

    private static final String EVENT_STUB = """
            package org.orecruncher.dsurround.lib.events;
            public interface IEvent<H> {
                H invoker();
            }
            """;

    private static final String PHASED_EVENT_STUB = """
            package org.orecruncher.dsurround.lib.events;
            public interface IPhasedEvent<H> extends IEvent<H> {
            }
            """;

    // Records failures instead of logging them. The create methods have the real signatures.
    private static final String FACTORY_STUB = """
            package org.orecruncher.dsurround.lib.events;
            import java.util.List;
            import java.util.function.Function;
            public final class EventingFactory {
                public static final List<String> FAILURES = new java.util.ArrayList<>();
                public static void handlerFailed(String eventName, Object handler, Throwable t) {
                    if (t instanceof VirtualMachineError fatal)
                        throw fatal;
                    FAILURES.add(eventName + ": " + t.getMessage());
                }
                public static <H> IPhasedEvent<H> createPrioritizedEvent(Function<List<H>, H> factory) {
                    var invoker = factory.apply(List.of());
                    return () -> invoker;
                }
                public static <H> IPhasedEvent<H> createPhasedEvent(Object phases, Function<List<H>, H> factory) {
                    return createPrioritizedEvent(factory);
                }
                public static <H> IEvent<H> createEvent(Function<List<H>, H> factory) {
                    var invoker = factory.apply(List.of());
                    return () -> invoker;
                }
            }
            """;

    /**
     * The EVENT line an interface needs, for fixtures.
     */
    private static String event(String name) {
        return "org.orecruncher.dsurround.lib.events.IPhasedEvent<" + name + "> EVENT = "
                + "org.orecruncher.dsurround.lib.events.EventingFactory.createPrioritizedEvent(" + name + "Invoker::create);";
    }

    private record Result(boolean success, List<String> errors, URLClassLoader loader) {
        Class<?> load(String name) throws ClassNotFoundException {
            return this.loader.loadClass(name);
        }
    }

    private static JavaFileObject source(String className, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    private Result compile(Map<String, String> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var classes = Files.createDirectories(this.output.resolve("classes"));
        var generated = Files.createDirectories(this.output.resolve("generated"));

        try (var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generated));

            var units = new ArrayList<JavaFileObject>();
            units.add(source("org.orecruncher.dsurround.lib.events.GenerateInvoker", ANNOTATION_STUB));
            units.add(source("org.orecruncher.dsurround.lib.events.EventingFactory", FACTORY_STUB));
            units.add(source("org.orecruncher.dsurround.lib.events.IEvent", EVENT_STUB));
            units.add(source("org.orecruncher.dsurround.lib.events.IPhasedEvent", PHASED_EVENT_STUB));
            sources.forEach((name, code) -> units.add(source(name, code)));

            var task = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:full"), null, units);
            task.setProcessors(List.of(new InvokerProcessor()));
            boolean success = task.call();

            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> d.getMessage(Locale.ROOT))
                    .toList();
            var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader());
            return new Result(success, errors, loader);
        }
    }

    private Result compile(String className, String code) throws IOException {
        return this.compile(Map.of(className, code));
    }

    private static String errorsOf(Result result) {
        return String.join("\n", result.errors());
    }

    /**
     * Runs {@code create} on the generated invoker with the handlers, and returns the invoker.
     */
    private static Object createInvoker(Result result, String invokerName, List<?> handlers) throws Exception {
        var invoker = result.load(invokerName);
        return invoker.getMethod("create", List.class).invoke(null, handlers);
    }

    /**
     * A handler for the interface, built with a dynamic proxy since the interface only exists in the test's class
     * loader.
     */
    private static Object handler(Class<?> iface, Consumer<Object[]> body) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
                return method.getName().equals("toString") ? "handler" : method.getName().equals("hashCode") ? System.identityHashCode(proxy) : proxy == args[0];
            body.accept(args == null ? new Object[0] : args);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> failures(Result result) throws Exception {
        return (List<String>) result.load("org.orecruncher.dsurround.lib.events.EventingFactory").getField("FAILURES").get(null);
    }

    // ---- Generated invokers ----------------------------------------------------------------------------------

    private static final String EVENTS = """
            package test;
            import org.orecruncher.dsurround.lib.events.GenerateInvoker;
            public final class Events {
                @GenerateInvoker
                @FunctionalInterface
                public interface IStep {
                    org.orecruncher.dsurround.lib.events.IPhasedEvent<IStep> EVENT =
                            org.orecruncher.dsurround.lib.events.EventingFactory.createPrioritizedEvent(Events_IStepInvoker::create);
                    void onStep(String name, int count);
                }
            }
            """;

    @Test
    void invokerCallsEveryHandlerInOrder() throws Exception {
        var result = this.compile("test.Events", EVENTS);
        assertTrue(result.success(), errorsOf(result));

        var iface = result.load("test.Events$IStep");
        var calls = new ArrayList<String>();
        var handlers = List.of(
                handler(iface, args -> calls.add("first " + args[0] + " " + args[1])),
                handler(iface, args -> calls.add("second " + args[0] + " " + args[1])));

        var invoker = createInvoker(result, "test.Events_IStepInvoker", handlers);
        iface.getMethod("onStep", String.class, int.class).invoke(invoker, "x", 3);

        assertEquals(List.of("first x 3", "second x 3"), calls);
    }

    @Test
    void handlerExceptionIsReportedAndTheRestStillRun() throws Exception {
        var result = this.compile("test.Events", EVENTS);
        var iface = result.load("test.Events$IStep");
        var calls = new ArrayList<String>();
        var handlers = List.of(
                handler(iface, args -> {
                    throw new IllegalStateException("boom");
                }),
                handler(iface, args -> calls.add("second ran")));

        var invoker = createInvoker(result, "test.Events_IStepInvoker", handlers);
        iface.getMethod("onStep", String.class, int.class).invoke(invoker, "x", 3);

        assertEquals(List.of("second ran"), calls);
        assertEquals(List.of("IStep: boom"), failures(result));
    }

    @Test
    void noHandlersDoesNothing() throws Exception {
        var result = this.compile("test.Events", EVENTS);
        var iface = result.load("test.Events$IStep");

        var invoker = createInvoker(result, "test.Events_IStepInvoker", List.of());

        assertDoesNotThrow(() -> iface.getMethod("onStep", String.class, int.class).invoke(invoker, "x", 3));
    }

    @Test
    void invokerIsAPlainObject() throws Exception {
        // Regression: the reflective proxy passed toString/hashCode/equals to the handlers
        var result = this.compile("test.Events", EVENTS);
        var iface = result.load("test.Events$IStep");
        var calls = new ArrayList<String>();

        var invoker = createInvoker(result, "test.Events_IStepInvoker", List.of(handler(iface, args -> calls.add("called"))));

        assertNotNull(invoker.toString());
        assertEquals(invoker, invoker);
        invoker.hashCode();
        assertEquals(List.of(), calls);
    }

    @Test
    void handlerListIsCopied() throws Exception {
        // The invoker keeps calling the handlers it was created with, whatever happens to the list afterwards
        var result = this.compile("test.Events", EVENTS);
        var iface = result.load("test.Events$IStep");
        var calls = new ArrayList<String>();
        var handlers = new ArrayList<Object>();
        handlers.add(handler(iface, args -> calls.add("original")));

        var invoker = createInvoker(result, "test.Events_IStepInvoker", handlers);
        handlers.add(handler(iface, args -> calls.add("added later")));
        iface.getMethod("onStep", String.class, int.class).invoke(invoker, "x", 3);

        assertEquals(List.of("original"), calls);
    }

    @Test
    void topLevelInterfaceAndOtherShapes() throws Exception {
        // Top level; no parameters; a parameter named like the invoker's own variables; generic parameter types;
        // a method inherited from a super interface; Object methods redeclared (still functional)
        var result = this.compile(Map.of(
                "test.IPlain", """
                        package test;
                        @org.orecruncher.dsurround.lib.events.GenerateInvoker
                        public interface IPlain {
                            %s
                            void run();
                        }
                        """.formatted(event("IPlain")),
                "test.IAwkward", """
                        package test;
                        @org.orecruncher.dsurround.lib.events.GenerateInvoker
                        public interface IAwkward {
                            %s
                            void on(java.util.List<? extends CharSequence> handlers, String handler, int[] array);
                            boolean equals(Object other);
                            String toString();
                        }
                        """.formatted(event("IAwkward")),
                "test.IBase", """
                        package test;
                        public interface IBase {
                            void inherited(String value);
                        }
                        """,
                "test.IDerived", """
                        package test;
                        @org.orecruncher.dsurround.lib.events.GenerateInvoker
                        public interface IDerived extends IBase {
                            %s
                        }
                        """.formatted(event("IDerived"))));
        assertTrue(result.success(), errorsOf(result));

        assertNotNull(result.load("test.IPlainInvoker"));
        assertNotNull(result.load("test.IAwkwardInvoker"));

        var derived = result.load("test.IDerived");
        var calls = new ArrayList<String>();
        var invoker = createInvoker(result, "test.IDerivedInvoker", List.of(handler(derived, args -> calls.add((String) args[0]))));
        derived.getMethod("inherited", String.class).invoke(invoker, "value");
        assertEquals(List.of("value"), calls);
    }

    @Test
    void nestedNamesAreFlattened() throws Exception {
        var result = this.compile("test.Outer", """
                package test;
                public class Outer {
                    public static class Middle {
                        @org.orecruncher.dsurround.lib.events.GenerateInvoker
                        public interface IInner {
                            org.orecruncher.dsurround.lib.events.IEvent<IInner> EVENT =
                                    org.orecruncher.dsurround.lib.events.EventingFactory.createEvent(Outer_Middle_IInnerInvoker::create);
                            void on();
                        }
                    }
                }
                """);
        assertTrue(result.success(), errorsOf(result));

        assertNotNull(result.load("test.Outer_Middle_IInnerInvoker"));
    }

    // ---- Errors ----------------------------------------------------------------------------------------------

    private void assertError(String code, String expected) throws IOException {
        var result = this.compile("test.Bad", code);

        assertFalse(result.success(), "should not compile");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains(expected)), errorsOf(result));
    }

    @Test
    void rejectsAClass() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public class Bad {
                }
                """, "can only be used on an interface");
    }

    @Test
    void rejectsNonVoidMethod() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public interface Bad {
                    boolean on();
                }
                """, "return void");
    }

    @Test
    void rejectsMoreThanOneMethod() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public interface Bad {
                    void one();
                    void two();
                }
                """, "exactly one abstract method; 'Bad' has 2");
    }

    @Test
    void rejectsNoMethod() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public interface Bad {
                    default void notAbstract() {}
                }
                """, "has 0");
    }

    @Test
    void rejectsGenericInterface() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public interface Bad<T> {
                    void on(T value);
                }
                """, "generic interfaces");
    }

    @Test
    void rejectsGenericMethod() throws IOException {
        this.assertError("""
                package test;
                @org.orecruncher.dsurround.lib.events.GenerateInvoker
                public interface Bad {
                    <T> void on(T value);
                }
                """, "generic handler method");
    }

    @Test
    void rejectsPrivateInterface() throws IOException {
        this.assertError("""
                package test;
                public class Bad {
                    @org.orecruncher.dsurround.lib.events.GenerateInvoker
                    private interface IHidden {
                        void on();
                    }
                }
                """, "private interface");
    }

    // ---- The EVENT field -------------------------------------------------------------------------------------

    private static final String IMPORTS = """
            package test;
            import org.orecruncher.dsurround.lib.events.*;
            """;

    @Test
    void acceptsTheUsualForms() throws IOException {
        // Prioritized, plain, phased, imported names, and a package qualified invoker
        var result = this.compile(Map.of(
                "test.IPrioritized", IMPORTS + """
                        @GenerateInvoker
                        public interface IPrioritized {
                            IPhasedEvent<IPrioritized> EVENT = EventingFactory.createPrioritizedEvent(IPrioritizedInvoker::create);
                            void on();
                        }
                        """,
                "test.IPlainEvent", IMPORTS + """
                        @GenerateInvoker
                        public interface IPlainEvent {
                            IEvent<IPlainEvent> EVENT = EventingFactory.createEvent(test.IPlainEventInvoker::create);
                            void on();
                        }
                        """,
                "test.IPhased", IMPORTS + """
                        @GenerateInvoker
                        public interface IPhased {
                            IPhasedEvent<IPhased> EVENT = EventingFactory.createPhasedEvent(null, IPhasedInvoker::create);
                            void on();
                        }
                        """));

        assertTrue(result.success(), errorsOf(result));
    }

    @Test
    void missingEventGivesTheLineToAdd() throws IOException {
        this.assertError(IMPORTS + """
                @GenerateInvoker
                public interface Bad {
                    void on();
                }
                """, "IPhasedEvent<Bad> EVENT = EventingFactory.createPrioritizedEvent(BadInvoker::create);");
    }

    @Test
    void eventForAnotherInterfaceIsRejected() throws IOException {
        var result = this.compile(Map.of(
                "test.IOther", IMPORTS + """
                        @GenerateInvoker
                        public interface IOther {
                            IPhasedEvent<IOther> EVENT = EventingFactory.createPrioritizedEvent(IOtherInvoker::create);
                            void on();
                        }
                        """,
                "test.Bad", IMPORTS + """
                        @GenerateInvoker
                        public interface Bad {
                            IPhasedEvent<IOther> EVENT = IOther.EVENT;
                            void on();
                        }
                        """));

        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("EVENT in 'Bad' should be IPhasedEvent<Bad> or IEvent<Bad>")), errorsOf(result));
    }

    @Test
    void eventOfTheWrongTypeIsRejected() throws IOException {
        this.assertError(IMPORTS + """
                @GenerateInvoker
                public interface Bad {
                    Object EVENT = new Object();
                    void on();
                }
                """, "should be IPhasedEvent<Bad> or IEvent<Bad>");
    }

    @Test
    void handWrittenInvokerIsRejected() throws IOException {
        // Compiles as far as types go; only the check catches that it bypasses the generated invoker
        this.assertError(IMPORTS + """
                @GenerateInvoker
                public interface Bad {
                    IPhasedEvent<Bad> EVENT = EventingFactory.createPrioritizedEvent(handlers -> () -> {});
                    void on();
                }
                """, "should be created from the generated invoker:");
    }

    @Test
    void factoryMustMatchTheEventType() throws IOException {
        // An IEvent made by the prioritized factory compiles (IPhasedEvent is an IEvent), but is the wrong pairing
        var result = this.compile("test.Bad", IMPORTS + """
                @GenerateInvoker
                public interface Bad {
                    IEvent<Bad> EVENT = EventingFactory.createPrioritizedEvent(BadInvoker::create);
                    void on();
                }
                """);

        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("IEvent<Bad> EVENT = EventingFactory.createEvent(BadInvoker::create);")), errorsOf(result));
    }

    @Test
    void nestedInterfaceSuggestionUsesTheFlattenedInvokerName() throws IOException {
        this.assertError(IMPORTS + """
                public class Bad {
                    @GenerateInvoker
                    public interface IInner {
                        void on();
                    }
                }
                """, "IPhasedEvent<IInner> EVENT = EventingFactory.createPrioritizedEvent(Bad_IInnerInvoker::create);");
    }
}
