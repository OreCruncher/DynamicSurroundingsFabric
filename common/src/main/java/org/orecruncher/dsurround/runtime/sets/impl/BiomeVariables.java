package org.orecruncher.dsurround.runtime.sets.impl;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;
import org.orecruncher.dsurround.runtime.sets.IBiomeVariables;

public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private final IBiomeLibrary biomeLibrary;
    private final IDimensionLibrary dimensionLibrary;

    private final Lazy<String> precipitationType;
    private final Lazy<String> id = new Lazy<>(() -> this.info.getBiomeId().toString());

    private Biome biome;
    private BiomeInfo info;

    public BiomeVariables(IBiomeLibrary biomeLibrary, IDimensionLibrary dimensionLibrary) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
        this.dimensionLibrary = dimensionLibrary;

        this.precipitationType = new Lazy<>(() -> {
            var player = GameUtils.getPlayer().orElseThrow();
            var pos = player.blockPosition();
            var seaLevel = this.dimensionLibrary.getData(player.level()).getSeaLevel();
            return this.biome.getPrecipitationAt(pos, seaLevel).name();
        });
    }

    @Override
    public IBiomeVariables getInterface() {
        return this;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            newBiome = player.level().getBiome(player.getOnPos()).value();
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
        return ((IBiomeExtended)((Object)this.biome)).dsurround_getWeather().downfall();
    }

    @Override
    public float getTemperature() {
        return this.biome.getBaseTemperature();
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