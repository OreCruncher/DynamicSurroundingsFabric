package org.orecruncher.dsurround.runtime;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.runtime.variables.*;

public final class ConditionEvaluator implements IConditionEvaluator {

    private final IModLog logger;
    private final ExecutionContext context;

    public ConditionEvaluator(IModLog logger) {
        this.logger = logger;
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
        return ScriptHelpers.toBoolean(this.eval(conditions));
    }

    public Object eval(final Script conditions) {
        try {
            return this.context.eval(conditions).orElse(false);
        } catch(ScriptException e) {
            var msg = e.getMessageForLogging(conditions.asString());
            this.logger.error(e, msg);
            return msg;
        } catch(Throwable t) {
            this.logger.error(t, "Unable to evaluate script");
        }
        return false;
    }
}
