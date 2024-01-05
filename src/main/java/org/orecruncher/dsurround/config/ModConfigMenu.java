package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.clothapi.ClothAPIFactory;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigOptions options = new ConfigOptions()
                    .setTranslationRoot("dsurround.config")
                    .setPropertyGroupStyle(Style.EMPTY.withColor(ColorPalette.GOLD.getValue()))
                    .setPropertyStyle(Style.EMPTY.withColor(ColorPalette.GRAY.getValue()))
                    .setTooltipStyle(Style.EMPTY.withColor(ColorPalette.WHITE.getValue()))
                    .setStripTitle(false);

            return new ClothAPIFactory(options, Client.Config).apply(GameUtils.getMC(), parent);
        };
    }
}
