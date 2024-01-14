package org.orecruncher.dsurround.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.platform.Services;

/**
 * Hook for ModMenu to get a hold of our configuration screen
 */
public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        var factory = Services.PLATFORM.getModConfigScreenFactory(Configuration.class);
        if (factory.isPresent()) {
            var f = factory.get();
            return screen -> f.create(GameUtils.getMC(), screen);
        }
        return null;
    }
}
