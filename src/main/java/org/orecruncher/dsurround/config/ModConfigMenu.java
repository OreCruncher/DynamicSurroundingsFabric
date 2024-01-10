package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.orecruncher.dsurround.lib.config.factories.FactoryResolver;

/**
 * Hook for ModMenu to get a hold of our configuration screen
 */
public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        var factory = FactoryResolver.getModConfigScreenFactory();
        if (factory == null)
            return null;
        return factory::create;
    }
}
