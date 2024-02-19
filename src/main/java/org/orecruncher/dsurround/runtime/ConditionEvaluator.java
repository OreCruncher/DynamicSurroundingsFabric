package org.orecruncher.dsurround.runtime;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.sets.impl.*;

import java.util.Optional;

public final class ConditionEvaluator implements IConditionEvaluator {

    private final ExecutionContext context;

    public ConditionEvaluator(IModLog logger) {
        this.context = new ExecutionContext("Conditions", logger);
        this.context.add(ContainerManager.resolve(BiomeVariables.class));
        this.context.add(ContainerManager.resolve(DimensionVariables.class));
        this.context.add(ContainerManager.resolve(DiurnalVariables.class));
        this.context.add(ContainerManager.resolve(PlayerVariables.class));
        this.context.add(ContainerManager.resolve(WeatherVariables.class));
        this.context.add(ContainerManager.resolve(EnvironmentState.class));
        this.context.add(ContainerManager.resolve(GlobalVariables.class));
        this.context.add(ContainerManager.resolve(SeasonVariables.class));

        ClientState.TICK_START.register(this::tick, HandlerPriority.VERY_HIGH);
    }

    public void tick(Minecraft client) {
        // Only want to tick while in game and the GUI is not paused.
        if (GameUtils.isInGame() && !client.isPaused())
            this.context.update();
    }

    public boolean check(final Script conditions) {
        final Object result = eval(conditions);
        return result instanceof Boolean && (boolean) result;
    }

    public Object eval(final Script conditions) {
        final Optional<Object> result = this.context.eval(conditions);
        return result.orElse(false);
    }
}
