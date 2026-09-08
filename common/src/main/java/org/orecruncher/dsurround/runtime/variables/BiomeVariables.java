package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

import java.util.List;

public class BiomeVariables extends VariableSet {

    private final IBiomeLibrary biomeLibrary;

    private final Lazy<String> precipitationType = new Lazy<>(() -> {
        var pos = GameUtils.getPlayer().orElseThrow().blockPosition();
        return this.biome.getPrecipitationAt(pos).name();
    });
    private final Lazy<String> id = new Lazy<>(() -> this.info.getBiomeId().toString());

    private Biome biome;
    private BiomeInfo info;

    public BiomeVariables(IBiomeLibrary biomeLibrary) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            newBiome = player.level().getBiome(player.getOnPos()).value();
        }
        this.setBiome(newBiome, variableAccess);
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
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("getModId"), 0, l -> this.info.getBiomeId().getNamespace());
        config.defineFunction(id("getId"), 0, l -> this.id.get());
        config.defineFunction(id("getName"), 0, l -> this.info.getBiomeName());
        config.defineFunction(id("getRainfall"), 0, l -> this.info.getDownfall());
        config.defineFunction(id("getTemperature"), 0, l -> this.biome.getBaseTemperature());
        config.defineFunction(id("getPrecipitationType"), 0, l -> this.precipitationType.get());
        config.defineFunction(id("getTraits"), 0, l -> this.info.getTraits().toString());
        config.defineFunction(id("is"), 1, l -> this.is(l.getFirst()));
        config.defineFunction(id("isAllOf"), -1, this::isAllOf);
        config.defineFunction(id("isOneOf"), -1, this::isOneOf);

        for (var trait : BiomeTrait.values())
            config.defineVariable(trait.getName(), () -> this.hasTrait(trait));
    }

    private boolean is(Object o) {
        return this.info != null && this.info.hasTrait(o.toString());
    }

    private boolean isAllOf(List<Object> trait) {
        for (var t : trait)
            if (!this.is(t))
                return false;
        return true;
    }

    private boolean isOneOf(List<Object> trait) {
        for (var t : trait)
            if (this.is(t))
                return true;
        return false;
    }

    private boolean hasTrait(BiomeTrait trait) {
        return this.info != null && this.info.getTraits().contains(trait);
    }
}