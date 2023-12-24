package org.orecruncher.dsurround.lib.platform;

import java.util.ServiceLoader;

public class Services {

    public static final IPlatform PLATFORM = resolveService(IPlatform.class);
    public static final IEventRegistrations EVENT_REGISTRATIONS = resolveService(IEventRegistrations.class);
    public static final ITagUtilities TAG_UTILITIES = resolveService(ITagUtilities.class);

    private static <T> T resolveService(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Unable to resolve service %s.  You forget to update META-INF/services?", clazz.getName())));
    }
}
