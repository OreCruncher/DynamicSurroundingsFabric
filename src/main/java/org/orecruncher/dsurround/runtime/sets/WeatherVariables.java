package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;

@Environment(EnvType.CLIENT)
public class WeatherVariables extends VariableSet<IWeatherVariables> implements IWeatherVariables {

    private final Lazy<Float> temperature = new Lazy<>(() -> {
        if (GameUtils.isInGame()) {
            final World world = GameUtils.getWorld();
            final BlockPos pos = GameUtils.getPlayer().getBlockPos();
            return WorldUtils.getTemperatureAt(world, pos);
        }
        return 0F;
    });
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIndensity;

    public WeatherVariables() {
        super("weather");
    }

    @Override
    public IWeatherVariables getInterface() {
        return this;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        if (GameUtils.isInGame()) {
            final World world = GameUtils.getWorld();
            this.rainIntensity = world.getRainGradient(1F);
            this.thunderIndensity = world.getThunderGradient(1F);
            this.isRaining = world.isRaining();
            this.isThundering = world.isThundering();
        } else {
            this.rainIntensity = 0F;
            this.thunderIndensity = 0F;
            this.isRaining = false;
            this.isThundering = false;
        }
        this.temperature.reset();
    }

    @Override
    public boolean isRaining() {
        return this.isRaining;
    }

    @Override
    public boolean isThundering() {
        return this.isThundering;
    }

    @Override
    public float getRainIntensity() {
        return this.rainIntensity;
    }

    @Override
    public float getThunderIntensity() {
        return this.thunderIndensity;
    }

    @Override
    public float getTemperature() {
        return this.temperature.get();
    }
}