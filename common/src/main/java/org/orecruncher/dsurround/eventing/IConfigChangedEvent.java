package org.orecruncher.dsurround.eventing;

import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IEvent;

/**
 * Event raised when a configuration is saved, after the change has been made.
 */
@GenerateInvoker
@FunctionalInterface
public interface IConfigChangedEvent {

    IEvent<IConfigChangedEvent> EVENT = EventingFactory.createEvent(IConfigChangedEventInvoker::create);

    void onChange(ConfigurationData config);
}
