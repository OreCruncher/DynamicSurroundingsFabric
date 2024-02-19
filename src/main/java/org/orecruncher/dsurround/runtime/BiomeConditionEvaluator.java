package org.orecruncher.dsurround.runtime;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.sets.impl.BiomeVariables;

import java.util.Optional;

public class BiomeConditionEvaluator {

    private final IModLog logger;
    private final BiomeVariables biomeVariables;
    private final ExecutionContext context;

    public BiomeConditionEvaluator(IBiomeLibrary biomeLibrary, IModLog logger) {
        this.logger = logger;
        this.context = new ExecutionContext("BiomeConditions", logger);
        this.biomeVariables = new BiomeVariables(biomeLibrary);
        this.context.add(this.biomeVariables);
    }

    public void reset() {
        this.biomeVariables.setBiome(null, null, this.context);
    }

    public boolean check(Biome biome, BiomeInfo info, final Script conditions) {
        final Object result = this.eval(biome, info, conditions);
        return result instanceof Boolean && (boolean) result;
    }

    public Object eval(Biome biome, final Script conditions) {
        return this.eval(biome, null, conditions);
    }

    public Object eval(Biome biome, BiomeInfo info, final Script conditions) {
        try {
            if (info == null)
                this.biomeVariables.setBiome(biome, this.context);
            else
                this.biomeVariables.setBiome(biome, info, this.context);
            final Optional<Object> result = this.context.eval(conditions);
            return result.orElse(false);
        } catch (Throwable t) {
            this.logger.error(t, "Unable to evaluate script");
        }
        return false;
    }
}
