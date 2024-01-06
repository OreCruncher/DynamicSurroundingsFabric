package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.orecruncher.dsurround.lib.config.clothapi.ClothAPIFactory;

public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClothAPIFactory::createDefaultConfigScreen;
    }
}
