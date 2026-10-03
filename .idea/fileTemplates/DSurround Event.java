#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME};#end

import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * ${Description}
 */
@GenerateInvoker
@FunctionalInterface
public interface ${NAME} {

    IPhasedEvent<${NAME}> EVENT = EventingFactory.createPrioritizedEvent(${NAME}Invoker::create);

    void ${Handler_method}();
}
