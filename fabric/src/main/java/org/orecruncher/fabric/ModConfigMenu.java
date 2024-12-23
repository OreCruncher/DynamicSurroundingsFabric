package org.orecruncher.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Hook for ModMenu to get a hold of our configuration screen
 */
public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> {
            var factory = this.acquireFactory();
            if (factory != null)
                return factory.create(screen);
            return null;
        };
    }

    private IScreenFactory<?> acquireFactory() {
        var logger = ContainerManager.resolve(IModLog.class);
        var provider = ContainerManager.resolve(IConfigScreenFactoryProvider.class);

        logger.info("ModMenu calling to get config screen");
        var factory = provider.getModConfigScreenFactory(Configuration.class);
        return factory.orElse(null);
    }
}
