package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BuiltinBiomes;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private Biome biome;
    private final Lazy<String> name = new Lazy<>(() -> BiomeLibrary.getBiomeName(this.biome));
    private final Lazy<String> modid = new Lazy<>(() -> BiomeLibrary.getBiomeId(this.biome).getNamespace());
    private final Lazy<String> id = new Lazy<>(() -> BiomeLibrary.getBiomeId(this.biome).toString());
    private final Lazy<String> category = new Lazy<>(() -> this.biome.getCategory().getName());
    private final Lazy<String> precipitationType = new Lazy<>(() -> this.biome.getPrecipitation().getName());
    private final Lazy<Collection<String>> traits = new Lazy<>(() -> BiomeLibrary.getBiomeTraits(this.biome));

    public BiomeVariables() {
        super("biome");
        setBiome(BuiltinBiomes.PLAINS);
    }

    public void setBiome(final Biome biome) {
        if (this.biome != biome) {
            update();
            this.biome = biome;
        }
    }

    @Override
    public IBiomeVariables getInterface() {
        return this;
    }

    @Override
    public void update() {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            newBiome = GameUtils.getPlayer().getEntityWorld().getBiome(GameUtils.getPlayer().getBlockPos());
        } else {
            newBiome = BuiltinBiomes.PLAINS;
        }

        if (newBiome != this.biome) {
            this.biome = newBiome;
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
        return String.join(" ", this.traits.get());
    }

    @Override
    public boolean hasTrait(String trait) {
        return this.traits.get().contains(trait.toLowerCase());
    }
}