package org.orecruncher.dsurround.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.config.ConfigOptions;
import org.orecruncher.dsurround.lib.config.clothapi.ClothAPIFactory;

@Environment(EnvType.CLIENT)
public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigOptions options = new ConfigOptions()
                    .setTranslationRoot("dsurround.config")
                    .setPropertyGroupStyle(Formatting.GOLD)
                    .setPropertyValueStyle(Formatting.GRAY)
                    .setTooltipStyle(Formatting.WHITE)
                    .setStripTitle(false);

            return new ClothAPIFactory(options, Client.Config, new Identifier("minecraft:textures/block/cobblestone.png"))
                    .apply(GameUtils.getMC(), parent);
        };
    }
}