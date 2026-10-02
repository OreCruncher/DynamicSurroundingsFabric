package org.orecruncher.dsurround.processor;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * Checks the {@code EVENT} field every {@code @GenerateInvoker} interface declares:
 * <pre>{@code
 * IPhasedEvent<IEntityStep> EVENT = EventingFactory.createPrioritizedEvent(IEntityStepInvoker::create);
 * }</pre>
 * It must exist, have the type {@code IEvent<ThisInterface>} or {@code IPhasedEvent<ThisInterface>}, and be created
 * by the matching {@code EventingFactory} method from this interface's generated invoker. A problem is reported as a
 * compile error giving the line to use.
 * <p>
 * The type is checked with the standard element API. The initializer can only be read from the source tree, through
 * javac's {@link Trees}; if that isn't available (another compiler, say) only the type is checked.
 */
final class EventFieldCheck {

    static final String FIELD = "EVENT";
    static final String EVENT_TYPE = "org.orecruncher.dsurround.lib.events.IEvent";
    static final String PHASED_EVENT_TYPE = "org.orecruncher.dsurround.lib.events.IPhasedEvent";

    private static final Set<String> EVENT_FACTORIES = Set.of("createEvent");
    private static final Set<String> PHASED_EVENT_FACTORIES = Set.of("createPrioritizedEvent", "createPhasedEvent");

    /**
     * A problem with the EVENT field, and where to report it (the field, or the interface if there is no field).
     */
    record Problem(Element element, String message) {
    }

    private final ProcessingEnvironment env;
    private final Trees trees;

    EventFieldCheck(ProcessingEnvironment env) {
        this.env = env;
        this.trees = treesFor(env);
    }

    /**
     * Whether initializers can be checked. False if javac's tree API isn't available.
     */
    boolean canCheckInitializers() {
        return this.trees != null;
    }

    /**
     * Finds javac's tree API. Gradle wraps the processing environment for incremental processing, and
     * {@link Trees#instance} only accepts javac's own, so if it is refused, look inside the wrapper for it.
     */
    private static Trees treesFor(ProcessingEnvironment env) {
        for (int depth = 0; env != null && depth < 5; depth++) {
            try {
                return Trees.instance(env);
            } catch (IllegalArgumentException | NoClassDefFoundError e) {
                env = wrapped(env);
            }
        }
        return null;
    }

    private static ProcessingEnvironment wrapped(ProcessingEnvironment env) {
        for (Class<?> c = env.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (ProcessingEnvironment.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (ProcessingEnvironment) field.get(env);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks the interface's EVENT field.
     *
     * @param invokerName the simple name of the interface's generated invoker
     * @return the problem, with the line to use, or null if there is none
     */
    Problem check(TypeElement type, String invokerName) {
        var name = type.getSimpleName().toString();
        var phasedLine = phasedLine(name, invokerName);
        var plainLine = plainLine(name, invokerName);

        var field = ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(f -> f.getSimpleName().contentEquals(FIELD))
                .findFirst()
                .orElse(null);
        if (field == null)
            return new Problem(type, String.format("@GenerateInvoker: '%s' needs its event as a field:%n    %s%nor, for an event without priorities:%n    %s",
                    name, phasedLine, plainLine));

        // Type: IEvent<ThisInterface> or IPhasedEvent<ThisInterface>
        var eventType = this.eventTypeOf(field, type);
        if (eventType == null)
            return new Problem(field, String.format("@GenerateInvoker: %s in '%s' should be IPhasedEvent<%s> or IEvent<%s>, e.g.%n    %s",
                    FIELD, name, name, name, phasedLine));
        boolean phased = eventType.equals(PHASED_EVENT_TYPE);
        var expectedLine = phased ? phasedLine : plainLine;

        // Initializer: EventingFactory.<matching factory>(..., ThisInvoker::create)
        if (this.trees != null) {
            var tree = this.trees.getTree(field);
            if (tree instanceof VariableTree variable && !this.isExpectedInitializer(variable, phased, invokerName))
                return new Problem(field, String.format("@GenerateInvoker: %s in '%s' should be created from the generated invoker:%n    %s",
                        FIELD, name, expectedLine));
        }
        return null;
    }

    static String phasedLine(String name, String invokerName) {
        return String.format("IPhasedEvent<%s> %s = EventingFactory.createPrioritizedEvent(%s::create);", name, FIELD, invokerName);
    }

    static String plainLine(String name, String invokerName) {
        return String.format("IEvent<%s> %s = EventingFactory.createEvent(%s::create);", name, FIELD, invokerName);
    }

    /**
     * The field's event type (IEvent or IPhasedEvent) if it is one of them parameterized with the interface,
     * otherwise null.
     */
    private String eventTypeOf(VariableElement field, TypeElement type) {
        if (field.asType().getKind() != TypeKind.DECLARED)
            return null;
        var declared = (DeclaredType) field.asType();
        var raw = ((TypeElement) declared.asElement()).getQualifiedName().toString();
        if (!raw.equals(EVENT_TYPE) && !raw.equals(PHASED_EVENT_TYPE))
            return null;
        var arguments = declared.getTypeArguments();
        if (arguments.size() != 1 || !this.env.getTypeUtils().isSameType(arguments.getFirst(), type.asType()))
            return null;
        return raw;
    }

    private boolean isExpectedInitializer(VariableTree variable, boolean phased, String invokerName) {
        if (!(variable.getInitializer() instanceof MethodInvocationTree call))
            return false;

        // EventingFactory.createX(...) or, statically imported, createX(...)
        String factory;
        if (call.getMethodSelect() instanceof MemberSelectTree select)
            factory = select.getIdentifier().toString();
        else if (call.getMethodSelect() instanceof IdentifierTree identifier)
            factory = identifier.getName().toString();
        else
            return false;
        if (!(phased ? PHASED_EVENT_FACTORIES : EVENT_FACTORIES).contains(factory))
            return false;

        // The invoker factory is the last argument: ThisInvoker::create, possibly package qualified
        var arguments = call.getArguments();
        if (arguments.isEmpty() || !(arguments.getLast() instanceof MemberReferenceTree reference))
            return false;
        if (!reference.getName().contentEquals("create"))
            return false;
        var qualifier = reference.getQualifierExpression().toString();
        return qualifier.equals(invokerName) || qualifier.endsWith("." + invokerName);
    }
}
