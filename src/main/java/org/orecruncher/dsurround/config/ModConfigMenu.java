package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ModMenuApi;
import org.orecruncher.dsurround.lib.config.clothapi.ConfigScreenFactory;

/**
 * Used for packs that have ModMenu installed
 */
public class ModConfigMenu implements ModMenuApi {

    @Override
    public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenFactory::createDefaultConfigScreen;
    }
}
