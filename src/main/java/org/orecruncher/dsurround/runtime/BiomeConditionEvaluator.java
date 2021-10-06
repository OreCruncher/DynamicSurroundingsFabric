package org.orecruncher.dsurround.runtime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.runtime.sets.*;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class BiomeConditionEvaluator {

    public static BiomeConditionEvaluator INSTANCE = new BiomeConditionEvaluator();

    private final ExecutionContext context;

    // Internal visibility for diagnostics
    final BiomeVariables biomeVariables;

    public BiomeConditionEvaluator() {
        this(true);
    }

    public BiomeConditionEvaluator(boolean cacheMethods) {
        this.context = new ExecutionContext("BiomeConditions", cacheMethods);
        this.biomeVariables = new BiomeVariables();
        this.context.add(this.biomeVariables);
    }

    public boolean check(Biome biome, final String conditions) {
        final Object result = eval(biome, conditions);
        return result instanceof Boolean && (boolean) result;
    }

    public Object eval(Biome biome, final String conditions) {
        try {
            if (conditions == null || conditions.length() == 0)
                return true;
            this.biomeVariables.setBiome(biome);
            final Optional<Object> result = this.context.eval(conditions);
            return result.orElse(false);
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to evaluate script");
        }
        return false;
    }
}
