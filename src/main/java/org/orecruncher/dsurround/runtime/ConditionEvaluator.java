package org.orecruncher.dsurround.runtime;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.platform.events.ClientState;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.processing.Scanners;
import org.orecruncher.dsurround.runtime.sets.*;

import java.util.Optional;

public final class ConditionEvaluator implements IConditionEvaluator {

    private final ExecutionContext context;

    public ConditionEvaluator() {
        this.context = new ExecutionContext("Conditions");
        this.context.add(new BiomeVariables(ContainerManager.resolve(IBiomeLibrary.class)));
        this.context.add(new DimensionVariables());
        this.context.add(new DiurnalVariables());
        this.context.add(new PlayerVariables());
        this.context.add(new WeatherVariables());
        this.context.add(new EnvironmentState(ContainerManager.resolve(Scanners.class)));

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
