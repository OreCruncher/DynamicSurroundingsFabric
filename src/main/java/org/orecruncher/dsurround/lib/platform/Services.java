package org.orecruncher.dsurround.lib.platform;

import org.orecruncher.dsurround.lib.platform.services.fabric.FabricEventRegistrations;
import org.orecruncher.dsurround.lib.platform.services.fabric.FabricPlatformService;

public class Services {

    public static final IPlatform PLATFORM = new FabricPlatformService();
    public static final IEventRegistrations EVENT_REGISTRATIONS = new FabricEventRegistrations();
}
