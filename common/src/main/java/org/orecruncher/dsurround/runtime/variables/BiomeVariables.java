package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.CachingSupplier;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class BiomeVariables extends VariableSet {

    private final IBiomeLibrary biomeLibrary;
    private final ILevelOracle levelOracle;

    private final CachingSupplier<String> precipitationType;
    private final CachingSupplier<String> id;
    private final CachingSupplier<String> biomeTraits;

    private Biome biome;
    private BiomeInfo info;

    public BiomeVariables(IBiomeLibrary biomeLibrary, ILevelOracle levelOracle) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
        this.levelOracle = levelOracle;

        this.precipitationType = CachingSupplier.from(() -> {
            var player = GameUtils.getPlayer().orElseThrow();
            return this.levelOracle.precipitationAt(player.getOnPos()).name();
        });

        this.id = CachingSupplier.from(() -> this.info.getBiomeId().toString());
        this.biomeTraits = CachingSupplier.from(() -> this.info.getTraits().toString());
    }

    @Override
    public void tick() {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            newBiome = this.levelOracle.biome(player.getOnPos());
        }
        this.setBiome(newBiome);
    }

    public void setBiome(final Biome biome) {
        if (biome != null) {
            BiomeInfo info = this.biomeLibrary.getBiomeInfo(biome);
            this.setBiome(biome, info);
        } else {
            this.setBiome(null, null);
        }
    }

    public void setBiome(final Biome biome, final BiomeInfo info) {
        this.biome = biome;
        this.info = info;
        this.id.clear();
        this.precipitationType.clear();
        this.biomeTraits.clear();
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("getModId"), l -> this.info.getBiomeId().getNamespace());
        config.defineFunction(id("getId"), l -> this.id.get());
        config.defineFunction(id("getName"), l -> this.info.getBiomeName());
        config.defineFunction(id("getRainfall"), l -> this.info.getDownfall());
        config.defineFunction(id("getTemperature"), l -> this.biome.getBaseTemperature());
        config.defineFunction(id("getPrecipitationType"), l -> this.precipitationType.get());
        config.defineFunction(id("getTraits"), l -> this.biomeTraits.get());
        config.defineFunction(id("is"), 1, false, l -> this.is(l[0]));
        config.defineFunction(id("isAllOf"), 1, true, this::isAllOf);
        config.defineFunction(id("isOneOf"), 1, true, this::isOneOf);

        for (var trait : BiomeTrait.values())
            config.defineVariable(trait.getName(), () -> this.hasTrait(trait));
    }

    private boolean is(final Object o) {
        return this.info != null && this.info.hasTrait(o.toString());
    }

    private boolean isAllOf(final Object[] trait) {
        for (var t : trait)
            if (!this.is(t))
                return false;
        return true;
    }

    private boolean isOneOf(final Object[] trait) {
        for (var t : trait)
            if (this.is(t))
                return true;
        return false;
    }

    private boolean hasTrait(BiomeTrait trait) {
        return this.info != null && this.info.hasTrait(trait);
    }
}