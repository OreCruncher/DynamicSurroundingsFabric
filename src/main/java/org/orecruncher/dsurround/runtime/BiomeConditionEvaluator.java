package org.orecruncher.dsurround.runtime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.sets.BiomeVariables;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class BiomeConditionEvaluator {

    public static BiomeConditionEvaluator INSTANCE = new BiomeConditionEvaluator();
    // Internal visibility for diagnostics
    final BiomeVariables biomeVariables;
    private final ExecutionContext context;

    public BiomeConditionEvaluator() {
        this.context = new ExecutionContext("BiomeConditions");
        this.biomeVariables = ContainerManager.resolve(BiomeVariables.class);
        this.context.add(this.biomeVariables);
    }

    public boolean check(Biome biome, final Script conditions) {
        final Object result = eval(biome, conditions);
        return result instanceof Boolean && (boolean) result;
    }

    public Object eval(Biome biome, final Script conditions) {
        try {
            this.biomeVariables.setBiome(biome);
            final Optional<Object> result = this.context.eval(conditions);
            return result.orElse(false);
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to evaluate script");
        }
        return false;
    }
}
