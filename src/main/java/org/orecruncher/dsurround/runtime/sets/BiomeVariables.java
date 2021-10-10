package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.biome.BiomeUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

@Environment(EnvType.CLIENT)
public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private Biome biome;
    private final Lazy<String> category = new Lazy<>(() -> this.biome.getCategory().getName());
    private final Lazy<String> precipitationType = new Lazy<>(() -> this.biome.getPrecipitation().getName());
    private BiomeInfo info;
    private final Lazy<String> name = new Lazy<>(() -> this.info.getBiomeName());
    private final Lazy<String> modid = new Lazy<>(() -> this.info.getBiomeId().getNamespace());
    private final Lazy<String> id = new Lazy<>(() -> this.info.getBiomeId().toString());
    private final Lazy<BiomeTraits> traits = new Lazy<>(() -> this.info.getTraits());

    public BiomeVariables() {
        super("biome");
    }

    @Override
    public IBiomeVariables getInterface() {
        return this;
    }

    @Override
    public void update() {
        Biome newBiome = BiomeUtils.DEFAULT_BIOME;
        if (GameUtils.isInGame()) {
            newBiome = GameUtils.getPlayer().getEntityWorld().getBiome(GameUtils.getPlayer().getBlockPos());
        }
        setBiome(newBiome);
    }

    public void setBiome(final Biome biome) {
        if (this.biome != biome) {
            this.biome = biome;
            this.info = BiomeLibrary.getBiomeInfo(this.biome);
            this.name.reset();
            this.modid.reset();
            this.id.reset();
            this.category.reset();
            this.precipitationType.reset();
            this.traits.reset();
        }
    }

    @Override
    public String getModId() {
        return this.modid.get();
    }

    @Override
    public String getId() {
        return this.id.get();
    }

    @Override
    public String getName() {
        return this.name.get();
    }

    @Override
    public float getRainfall() {
        return this.biome.getDownfall();
    }

    @Override
    public float getTemperature() {
        return this.biome.getTemperature();
    }

    @Override
    public String getCategory() {
        return this.category.get();
    }

    @Override
    public String getPrecipitationType() {
        return this.precipitationType.get();
    }

    @Override
    public String getTraits() {
        return this.traits.get().toString();
    }

    @Override
    public boolean is(String trait) {
        return this.traits.get().contains(trait);
    }

    @Override
    public boolean isAllOf(String... trait) {
        if (trait == null || trait.length == 0)
            return false;
        var traits = this.traits.get();
        for (var t : trait)
            if (!traits.contains(t))
                return false;
        return true;
    }

    @Override
    public boolean isOneOf(String... trait) {
        if (trait == null || trait.length == 0)
            return false;
        var traits = this.traits.get();
        for (var t : trait)
            if (traits.contains(t))
                return true;
        return false;
    }
}