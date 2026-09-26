package org.orecruncher.dsurround.runtime;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.variables.BiomeVariables;

public final class BiomeConditionEvaluator {

    private final IModLog logger;
    private final BiomeVariables biomeVariables;
    private final ExecutionContext context;

    public BiomeConditionEvaluator(IBiomeLibrary biomeLibrary, IModLog logger) {
        this.logger = logger;
        this.context = new ExecutionContext("BiomeConditions", logger);
        this.biomeVariables = new BiomeVariables(biomeLibrary, ContainerManager.resolve(ILevelOracle.class));
        this.context.add(this.biomeVariables);
        this.context.configureScripting(ContainerManager.resolve(PlatformFunctions.class));
    }

    public void reset() {
        this.biomeVariables.setBiome(null, null);
    }

    public boolean check(Biome biome, BiomeInfo info, final Script conditions) {
        return ScriptHelpers.toBoolean(this.eval(biome, info, conditions));
    }

    public Object eval(Biome biome, final Script conditions) {
        return this.eval(biome, null, conditions);
    }

    public Object eval(Biome biome, BiomeInfo info, final Script conditions) {
        try {
            if (info == null)
                this.biomeVariables.setBiome(biome);
            else
                this.biomeVariables.setBiome(biome, info);
            return this.context.eval(conditions).orElse(false);
        } catch (ScriptException e) {
            var msg = e.getMessageForLogging(conditions.asString());
            this.logger.error(e, msg);
        } catch (Throwable t) {
            this.logger.error(t, "Unable to evaluate script");
        }
        return false;
    }
}
