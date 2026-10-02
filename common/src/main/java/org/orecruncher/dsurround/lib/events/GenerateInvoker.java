package org.orecruncher.dsurround.lib.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event handler interface so the build generates its invoker: a class named after the interface (any
 * enclosing types joined with underscores, then "Invoker") with a {@code create} method to pass when creating the
 * event. The interface declares its event as an {@code EVENT} field created from that invoker:
 * <pre>{@code
 * @GenerateInvoker
 * @FunctionalInterface
 * public interface IEntityStep {
 *
 *     IPhasedEvent<IEntityStep> EVENT = EventingFactory.createPrioritizedEvent(IEntityStepInvoker::create);
 *
 *     void onStep(Entity entity, BlockPos stepPosition, BlockState blockState);
 * }
 * }</pre>
 * Handlers register with {@code IEntityStep.EVENT.register(...)}, and the event is raised with
 * {@code IEntityStep.EVENT.invoker().onStep(...)}. The invoker calls every handler in turn; one that throws is
 * reported through {@link EventingFactory#handlerFailed} and the rest still run.
 * <p>
 * The interface must be a non-private functional interface whose method returns void, and must declare
 * {@code EVENT} as above: {@code IPhasedEvent} with {@code createPrioritizedEvent} (or {@code createPhasedEvent}),
 * or {@code IEvent} with {@code createEvent}, from the generated invoker. Otherwise the build fails with an error
 * giving the line to write. In IntelliJ, New > DSurround Event creates the file with all of this filled in.
 * <p>
 * The generator is the {@code processor} subproject; what the invokers do is defined by its template.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateInvoker {
}
