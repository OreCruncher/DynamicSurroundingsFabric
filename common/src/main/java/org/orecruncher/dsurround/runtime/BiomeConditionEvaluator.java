package org.orecruncher.dsurround.runtime;

import org.orecruncher.dsurround.lib.logging.LogThrottle;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.IBiomeIdentity;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.variables.BiomeVariables;

public final class BiomeConditionEvaluator {

    private final BiomeVariables biomeVariables;
    private final ExecutionContext context;
    // Every rule is checked against every biome, so a biome that can't be set up is reported once per reload
    private final LogThrottle<Biome> failures;

    public BiomeConditionEvaluator(IBiomeLibrary biomeLibrary, IModLog logger, PlatformFunctions platformFunctions) {
        this.context = new ExecutionContext("BiomeConditions", logger);
        this.biomeVariables = BiomeVariables.forBiomeRules(biomeLibrary);
        this.context.add(this.biomeVariables);
        this.context.configureScripting(platformFunctions);
        this.failures = LogThrottle.oncePerKey(logger, "biome script setup failures", "the next reload");
    }

    public void reset() {
        this.biomeVariables.setBiome(null, null);
        this.failures.reset();
    }

    public boolean check(Biome biome, IBiomeIdentity info, final Script conditions) {
        // Evaluates directly to a boolean. A script that fails, or whose result cannot be converted to a boolean,
        // is treated as false and the problem is logged once.
        return this.setBiome(biome, info) && this.context.check(conditions);
    }

    public Object eval(Biome biome, final Script conditions) {
        return this.eval(biome, null, conditions);
    }

    public Object eval(Biome biome, IBiomeIdentity info, final Script conditions) {
        // ExecutionContext.eval() handles and logs script errors itself
        return this.setBiome(biome, info) ? this.context.eval(conditions).orElse(false) : false;
    }

    /**
     * Sets the biome the scripts see.
     * @return False if setting up the biome failed (the problem is logged)
     */
    private boolean setBiome(Biome biome, IBiomeIdentity info) {
        try {
            if (info == null)
                this.biomeVariables.setBiome(biome);
            else
                this.biomeVariables.setBiome(biome, info);
            return true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            this.failures.error(biome, t, "Unable to evaluate scripts for biome %s", biome);
            return false;
        }
    }
}
