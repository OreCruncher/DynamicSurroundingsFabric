package org.orecruncher.dsurround.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates the invoker for each event handler interface marked {@code @GenerateInvoker}: a class with a
 * {@code create(List<Handler>)} method returning a Handler that calls every registered handler in turn.
 * <p>
 * For {@code ClientState.IClientTickStart} in package {@code p} it writes {@code p.ClientState_IClientTickStartInvoker}
 * (nesting is flattened with underscores, so names can't collide within a package).
 * <p>
 * The interface must be a non-private functional interface whose method returns void and has no type parameters,
 * and must declare its event as an {@code EVENT} field created from the invoker (see {@link EventFieldCheck}).
 * Anything else is a compile error on the interface, giving what to write instead.
 * <p>
 * <b>What the invoker does is defined here</b>, in {@link #writeInvoker}: change the template and every invoker is
 * regenerated on the next build. Currently each handler runs inside a try/catch, and a handler that throws is
 * passed to {@value #FAILURE_HANDLER}, so the remaining handlers still run.
 */
@SupportedAnnotationTypes(InvokerProcessor.ANNOTATION)
public final class InvokerProcessor extends AbstractProcessor {

    static final String ANNOTATION = "org.orecruncher.dsurround.lib.events.GenerateInvoker";
    static final String FAILURE_HANDLER = "org.orecruncher.dsurround.lib.events.EventingFactory.handlerFailed";
    static final String SUFFIX = "Invoker";

    private EventFieldCheck eventFieldCheck;
    private boolean notedUncheckedInitializers;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.INTERFACE) {
                    this.error(element, "@GenerateInvoker can only be used on an interface");
                    continue;
                }
                var type = (TypeElement) element;
                var method = this.findHandlerMethod(type);
                if (method != null && this.isSupported(type, method)) {
                    this.generate(type, method);
                    this.checkEventField(type);
                }
            }
        }
        return true;
    }

    private void checkEventField(TypeElement type) {
        if (this.eventFieldCheck == null)
            this.eventFieldCheck = new EventFieldCheck(this.processingEnv);
        if (!this.eventFieldCheck.canCheckInitializers() && !this.notedUncheckedInitializers) {
            this.notedUncheckedInitializers = true;
            this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "@GenerateInvoker: javac's tree API isn't available, so EVENT initializers are not checked (types still are)");
        }
        var problem = this.eventFieldCheck.check(type, invokerName(type));
        if (problem != null)
            this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, problem.message(), problem.element());
    }

    /**
     * The interface's single abstract method, or null (with an error reported) if there isn't exactly one.
     * Abstract redeclarations of Object's methods (equals, hashCode, toString) don't count, as for any functional
     * interface.
     */
    private ExecutableElement findHandlerMethod(TypeElement type) {
        var elements = this.processingEnv.getElementUtils();
        var objectMethods = ElementFilter.methodsIn(elements.getTypeElement("java.lang.Object").getEnclosedElements());

        var candidates = new ArrayList<ExecutableElement>();
        for (var method : ElementFilter.methodsIn(elements.getAllMembers(type))) {
            if (!method.getModifiers().contains(Modifier.ABSTRACT))
                continue;
            if (objectMethods.stream().anyMatch(o -> elements.overrides(method, o, type) || this.sameSignature(method, o)))
                continue;
            candidates.add(method);
        }

        if (candidates.size() != 1) {
            this.error(type, "@GenerateInvoker needs an interface with exactly one abstract method; '%s' has %d",
                    type.getSimpleName(), candidates.size());
            return null;
        }
        return candidates.getFirst();
    }

    private boolean sameSignature(ExecutableElement a, ExecutableElement b) {
        return a.getSimpleName().contentEquals(b.getSimpleName())
                && this.processingEnv.getTypeUtils().isSubsignature((ExecutableType) a.asType(), (ExecutableType) b.asType());
    }

    private boolean isSupported(TypeElement type, ExecutableElement method) {
        boolean ok = true;
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            this.error(type, "@GenerateInvoker can't be used on a private interface; the generated invoker couldn't use it");
            ok = false;
        }
        if (!type.getTypeParameters().isEmpty()) {
            this.error(type, "@GenerateInvoker doesn't support generic interfaces");
            ok = false;
        }
        if (!method.getTypeParameters().isEmpty()) {
            this.error(method, "@GenerateInvoker doesn't support a generic handler method");
            ok = false;
        }
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            this.error(method, "@GenerateInvoker needs the handler method to return void; with several handlers there is no single result");
            ok = false;
        }
        return ok;
    }

    /**
     * The generated class's simple name: the interface's name with any enclosing types, joined by underscores,
     * then {@value #SUFFIX}.
     */
    static String invokerName(TypeElement type) {
        var parts = new ArrayList<String>();
        Element e = type;
        while (e instanceof TypeElement t) {
            parts.addFirst(t.getSimpleName().toString());
            e = t.getEnclosingElement();
        }
        return String.join("_", parts) + SUFFIX;
    }

    private void generate(TypeElement type, ExecutableElement method) {
        var elements = this.processingEnv.getElementUtils();
        var packageName = elements.getPackageOf(type).getQualifiedName().toString();
        var className = invokerName(type);
        var qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

        try {
            // The interface is the originating element. The processor is declared aggregating, not isolating: the
            // invokers call EventingFactory, so a change there makes Gradle recompile them, and an isolating processor
            // only regenerates invokers whose interface was itself recompiled (an interface in its own file wasn't).
            var file = this.processingEnv.getFiler().createSourceFile(qualifiedName, type);
            try (var writer = file.openWriter()) {
                writeInvoker(writer, packageName, className, type.getQualifiedName().toString(),
                        type.getSimpleName().toString(), method.getSimpleName().toString(), parameterNames(method));
            }
        } catch (IOException e) {
            this.error(type, "Unable to write %s: %s", qualifiedName, e.getMessage());
        }
    }

    private static List<String> parameterNames(ExecutableElement method) {
        return method.getParameters().stream().map(p -> p.getSimpleName().toString()).toList();
    }

    /**
     * The template for a generated invoker.
     * <p>
     * The returned lambda leaves the parameter types to be inferred from the interface, so the generated code
     * never has to spell out (possibly annotated or generic) types. Locals start with '$' so they can't clash with
     * the handler method's parameter names.
     */
    static void writeInvoker(Writer out, String packageName, String className, String handlerType, String eventName,
                             String methodName, List<String> parameters) throws IOException {
        var params = String.join(", ", parameters);
        var pkg = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";

        out.write(pkg + """
                /**
                 * Generated from {@link %2$s} by %5$s; do not edit.
                 */
                @javax.annotation.processing.Generated("%5$s")
                public final class %1$s {

                    private %1$s() {
                    }

                    /**
                     * Creates the invoker for {@link %2$s}: calls each handler in the order given. A handler that throws
                     * is reported and the remaining handlers still run.
                     */
                    public static %2$s create(java.util.List<%2$s> $handlers) {
                        final %2$s[] $array = $handlers.toArray(new %2$s[0]);
                        return (%4$s) -> {
                            for (final %2$s $handler : $array) {
                                try {
                                    $handler.%3$s(%4$s);
                                } catch (Throwable $t) {
                                    %6$s("%7$s", $handler, $t);
                                }
                            }
                        };
                    }
                }
                """.formatted(className, handlerType, methodName, params, InvokerProcessor.class.getName(),
                FAILURE_HANDLER, eventName));
    }

    private void error(Element element, String format, Object... args) {
        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, args), element);
    }
}
