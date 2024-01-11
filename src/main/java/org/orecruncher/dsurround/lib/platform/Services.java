package org.orecruncher.dsurround.lib.platform;

import java.util.ServiceLoader;

public class Services {

    public static final IPlatform PLATFORM = load(IPlatform.class);
    public static final IClientEventRegistrations CLIENT_EVENT_REGISTRATIONS = load(IClientEventRegistrations.class);

    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}

