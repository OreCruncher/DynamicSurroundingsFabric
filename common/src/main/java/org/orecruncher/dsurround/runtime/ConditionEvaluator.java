package org.orecruncher.dsurround.runtime;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.variables.*;

public final class ConditionEvaluator implements IConditionEvaluator {

    private final IModLog logger;
    private final ExecutionContext context;

    public ConditionEvaluator(IModLog logger) {
        this.logger = logger;
        this.context = new ExecutionContext("Conditions", logger);
        this.context.configureScripting(ContainerManager.resolve(PlatformFunctions.class));
        this.context.add(ContainerManager.resolve(BiomeVariables.class));
        this.context.add(ContainerManager.resolve(DimensionVariables.class));
        this.context.add(ContainerManager.resolve(DiurnalVariables.class));
        this.context.add(ContainerManager.resolve(PlayerVariables.class));
        this.context.add(ContainerManager.resolve(WeatherVariables.class));
        this.context.add(ContainerManager.resolve(EnvironmentState.class));
        this.context.add(ContainerManager.resolve(GlobalVariables.class));
        this.context.add(ContainerManager.resolve(SeasonVariables.class));

        IClientTickStart.EVENT.register(this::tick, HandlerPriority.VERY_HIGH);
    }

    public void tick(Minecraft client) {
        // Only want to tick while in game and the GUI is not paused.
        if (GameUtils.isInGame() && !client.isPaused())
            this.context.tick();
    }

    public boolean check(final Script conditions) {
        // Evaluates directly to a boolean. A script that fails, or whose result cannot be converted to a boolean,
        // is treated as false and the problem is logged once.
        return this.context.check(conditions);
    }

    public Object eval(final Script conditions) {
        // ExecutionContext.eval() handles and logs script errors itself
        return this.context.eval(conditions).orElse(false);
    }
}
