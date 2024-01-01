package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.ChatFormatting;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.clothapi.ClothAPIFactory;

public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigOptions options = new ConfigOptions()
                    .setTranslationRoot("dsurround.config")
                    .setPropertyGroupStyle(ChatFormatting.GOLD)
                    .setPropertyValueStyle(ChatFormatting.GRAY)
                    .setTooltipStyle(ChatFormatting.WHITE)
                    .setStripTitle(false);

            return new ClothAPIFactory(options, Client.Config).apply(GameUtils.getMC(), parent);
        };
    }
}
