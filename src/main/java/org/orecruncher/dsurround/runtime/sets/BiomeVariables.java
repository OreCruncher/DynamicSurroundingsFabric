package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

@Environment(EnvType.CLIENT)
public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private Biome biome;
    private BiomeInfo info;
    private final Lazy<String> precipitationType = new Lazy<>(() -> {
        var pos = GameUtils.getPlayer().getBlockPos();
        return this.biome.getPrecipitation(pos).asString();
    });
    private final Lazy<String> id = new Lazy<>(() -> this.info.getBiomeId().toString());

    public BiomeVariables() {
        super("biome");
    }

    @Override
    public IBiomeVariables getInterface() {
        return this;
    }

    @Override
    public void update() {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            newBiome = GameUtils.getPlayer().getEntityWorld().getBiome(GameUtils.getPlayer().getBlockPos()).value();
        }
        setBiome(newBiome);
    }

    public void setBiome(final Biome biome) {
        if (this.biome != biome) {
            this.biome = biome;
            this.info = BiomeLibrary.getBiomeInfo(this.biome);
            this.id.reset();
            this.precipitationType.reset();
        }
    }

    @Override
    public String getModId() {
        return this.info.getBiomeId().getNamespace();
    }

    @Override
    public String getId() {
        return this.id.get();
    }

    @Override
    public String getName() {
        return this.info.getBiomeName();
    }

    @Override
    public float getRainfall() {
        return this.biome.weather.downfall();
    }

    @Override
    public float getTemperature() {
        return this.biome.getTemperature();
    }

    @Override
    public String getPrecipitationType() {
        return this.precipitationType.get();
    }

    @Override
    public String getTraits() {
        return this.info.getTraits().toString();
    }

    @Override
    public boolean is(String trait) {
        return this.info.hasTrait(trait);
    }

    @Override
    public boolean isAllOf(String... trait) {
        if (trait == null)
            return false;
        for (var t : trait)
            if (!this.is(t))
                return false;
        return true;
    }

    @Override
    public boolean isOneOf(String... trait) {
        if (trait == null)
            return false;
        for (var t : trait)
            if (this.is(t))
                return true;
        return false;
    }
}