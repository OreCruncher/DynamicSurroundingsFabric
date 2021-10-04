package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BuiltinBiomes;
import org.orecruncher.dsurround.lib.BiomeUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

@Environment(EnvType.CLIENT)
public class BiomeVariables extends VariableSet<IBiomeVariables> implements IBiomeVariables {

    private Biome biome;
    private final Lazy<String> name = new Lazy<>(() -> BiomeUtils.getBiomeName(this.biome));
    private final Lazy<String> modid = new Lazy<>(() -> BiomeUtils.getBiomeId(this.biome).getNamespace());
    private final Lazy<String> id = new Lazy<>(() -> BiomeUtils.getBiomeId(this.biome).toString());
    private final Lazy<String> category = new Lazy<>(() -> this.biome.getCategory().getName());
    private final Lazy<String> rainType = new Lazy<>(() -> this.biome.getPrecipitation().getName());

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
            this.rainType.reset();
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
    public String getRainType() {
        return this.rainType.get();
    }
}