package org.orecruncher.dsurround.runtime.sets;

import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private final IBiomeLibrary biomeLibrary;

    private final Lazy<String> precipitationType = new Lazy<>(() -> {
        var pos = GameUtils.getPlayer().getBlockPos();
        return this.biome.getPrecipitation(pos).asString();
    });
    private final Lazy<String> id = new Lazy<>(() -> this.info.getBiomeId().toString());

    private Biome biome;
    private BiomeInfo info;

    public BiomeVariables(IBiomeLibrary biomeLibrary) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
    }

    @Override
    public IBiomeVariables getInterface() {
        return this;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            newBiome = GameUtils.getPlayer().getEntityWorld().getBiome(GameUtils.getPlayer().getBlockPos()).value();
        }
        setBiome(newBiome, variableAccess);
    }

    public void setBiome(final Biome biome, IVariableAccess variableAccess) {
        if (biome != null) {
            BiomeInfo info = this.biomeLibrary.getBiomeInfo(biome);
            this.setBiome(biome, info, variableAccess);
        } else {
            this.setBiome(null, null, variableAccess);
        }
    }

    public void setBiome(final Biome biome, final BiomeInfo info, IVariableAccess variableAccess) {
        this.biome = biome;
        this.info = info;
        this.id.reset();
        this.precipitationType.reset();

        // Clear out any previous trait settings
        for (var trait : BiomeTrait.values())
            variableAccess.put(trait.getName(), false);

        if (this.info != null) {
            // Set true the trait variables associated with the biome
            this.info.getTraits().forEach(trait -> variableAccess.put(trait.getName(), true));
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