package org.orecruncher.dsurround.runtime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.sets.*;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class ConditionEvaluator {

    public static ConditionEvaluator INSTANCE = new ConditionEvaluator();

    static {
    }

    // Internal visibility for diagnostics
    final BiomeVariables biomeVariables;
    final DimensionVariables dimensionVariables;
    final DiurnalVariables diurnalVariables;
    final PlayerVariables playerVariables;
    final WeatherVariables weatherVariables;
    final EnvironmentState environmentState;
    private final ExecutionContext context;

    public ConditionEvaluator() {
        var container = ContainerManager.getDefaultContainer();
        this.biomeVariables = container.resolve(BiomeVariables.class);
        this.dimensionVariables = container.resolve(DimensionVariables.class);
        this.diurnalVariables = container.resolve(DiurnalVariables.class);
        this.playerVariables = container.resolve(PlayerVariables.class);
        this.weatherVariables = container.resolve(WeatherVariables.class);
        this.environmentState = container.resolve(EnvironmentState.class);

        this.context = new ExecutionContext("Conditions");
        this.context.add(this.biomeVariables);
        this.context.add(this.dimensionVariables);
        this.context.add(this.diurnalVariables);
        this.context.add(this.playerVariables);
        this.context.add(this.weatherVariables);
        this.context.add(this.environmentState);

        // Setup ticker for the variables.  Only want to tick while in game and
        // the GUI is not paused.
        ClientState.TICK_START.register(client -> {
            if (GameUtils.isInGame() && !client.isPaused())
                this.tick();
        });
    }

    public void tick() {
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
