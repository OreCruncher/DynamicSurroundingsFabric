package org.orecruncher.dsurround.runtime;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.variables.*;

public final class ConditionEvaluator implements IConditionEvaluator {

    private final ExecutionContext context;
    private boolean wasInGame;

    public ConditionEvaluator(IModLog logger, PlatformFunctions platformFunctions,
                              BiomeVariables biomeVariables, DimensionVariables dimensionVariables,
                              DiurnalVariables diurnalVariables, PlayerVariables playerVariables,
                              WeatherVariables weatherVariables, EnvironmentState environmentState,
                              GlobalVariables globalVariables, SeasonVariables seasonVariables) {
        this.context = new ExecutionContext("Conditions", logger);
        this.context.configureScripting(platformFunctions);
        this.context.add(biomeVariables);
        this.context.add(dimensionVariables);
        this.context.add(diurnalVariables);
        this.context.add(playerVariables);
        this.context.add(weatherVariables);
        this.context.add(environmentState);
        this.context.add(globalVariables);
        this.context.add(seasonVariables);

        IClientTickStart.EVENT.register(this::tick, HandlerPriority.VERY_HIGH);
    }

    public void tick(Minecraft client) {
        var inGame = GameUtils.isInGame();
        if (shouldTick(inGame, client.isPaused(), this.wasInGame))
            this.context.tick();
        this.wasInGame = inGame;
    }

    /**
     * Whether to update the variables this tick: while in game and not paused, and once more on the first tick
     * after leaving the game, so the variables drop the last world's values for their out-of-game defaults.
     */
    static boolean shouldTick(boolean inGame, boolean paused, boolean wasInGame) {
        return inGame ? !paused : wasInGame;
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
