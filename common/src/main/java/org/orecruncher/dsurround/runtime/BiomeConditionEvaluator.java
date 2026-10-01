package org.orecruncher.dsurround.runtime;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.variables.BiomeVariables;

public final class BiomeConditionEvaluator {

    private final IModLog logger;
    private final BiomeVariables biomeVariables;
    private final ExecutionContext context;

    public BiomeConditionEvaluator(IBiomeLibrary biomeLibrary, IModLog logger) {
        this.logger = logger;
        this.context = new ExecutionContext("BiomeConditions", logger);
        this.biomeVariables = new BiomeVariables(biomeLibrary);
        this.context.add(this.biomeVariables);
        this.context.configureScripting(ContainerManager.resolve(PlatformFunctions.class));
    }

    public void reset() {
        this.biomeVariables.setBiome(null, null);
    }

    public boolean check(Biome biome, BiomeInfo info, final Script conditions) {
        // Evaluates directly to a boolean. A script that fails, or whose result cannot be converted to a boolean,
        // is treated as false and the problem is logged once.
        return this.setBiome(biome, info) && this.context.check(conditions);
    }

    public Object eval(Biome biome, final Script conditions) {
        return this.eval(biome, null, conditions);
    }

    public Object eval(Biome biome, BiomeInfo info, final Script conditions) {
        // ExecutionContext.eval() handles and logs script errors itself
        return this.setBiome(biome, info) ? this.context.eval(conditions).orElse(false) : false;
    }

    /**
     * Sets the biome the scripts see.
     * @return False if setting up the biome failed (the problem is logged)
     */
    private boolean setBiome(Biome biome, BiomeInfo info) {
        try {
            if (info == null)
                this.biomeVariables.setBiome(biome);
            else
                this.biomeVariables.setBiome(biome, info);
            return true;
        } catch (Throwable t) {
            this.logger.error(t, "Unable to evaluate script");
            return false;
        }
    }
}
