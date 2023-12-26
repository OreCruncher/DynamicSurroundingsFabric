package org.orecruncher.dsurround.commands;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;

public class BiomeCommandHandler {

    public static Text execute(Identifier biomeIdentifier, String script) {
        return GameUtils.getRegistryManager()
                .map(rm -> {
                    var biome = rm.get(RegistryKeys.BIOME).get(biomeIdentifier);
                    if (biome == null) {
                        return Text.stringifiedTranslatable("dsurround.command.dsbiome.failure.unknown_biome", biomeIdentifier);
                    }
                    var result = ContainerManager.resolve(IBiomeLibrary.class).eval(biome, new Script(script));
                    return Text.literal(result.toString());
                })
                .orElse(Text.literal("Unable to locate registry manager"));
    }
}
