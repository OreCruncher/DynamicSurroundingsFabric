package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.CachingSupplier;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.ScriptArguments;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public final class BiomeVariables extends VariableSet {

    private static final String UNKNOWN = "UNKNOWN";

    /**
     * A biome trait name, case-insensitive. Constant names are checked when the script is compiled, so a typo
     * such as biome.is('HOTT') is reported as an error instead of never matching.
     */
    private static final ArgType<BiomeTrait> BIOME_TRAIT = ArgType.of("biome trait", BiomeVariables::toTrait);

    private final IBiomeLibrary biomeLibrary;

    private final CachingSupplier<String> precipitationType = CachingSupplier.from(() -> {
        var player = GameUtils.getPlayer();
        if (this.biome == null || player.isEmpty())
            return Biome.Precipitation.NONE.name();
        return this.biome.getPrecipitationAt(player.get().blockPosition()).name();
    });
    private final CachingSupplier<String> id = CachingSupplier.from(() -> this.info == null ? UNKNOWN : this.info.getBiomeId().toString());
    private final CachingSupplier<String> biomeTraits = CachingSupplier.from(() -> this.info == null ? "[]" : this.info.getTraits().toString());

    private Biome biome;
    private BiomeInfo info;

    public BiomeVariables(IBiomeLibrary biomeLibrary) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
    }

    @Override
    public void tick() {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            newBiome = player.level().getBiome(player.getOnPos()).value();
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
        // info and biome are null when not in game
        config.property(id("getModId"), () -> this.info == null ? UNKNOWN : this.info.getBiomeId().getNamespace());
        config.property(id("getId"), this.id::get);
        config.property(id("getName"), () -> this.info == null ? UNKNOWN : this.info.getBiomeName());
        config.property(id("getRainfall"), () -> this.info == null ? 0F : this.info.getDownfall());
        config.property(id("getTemperature"), () -> this.biome == null ? 0F : this.biome.getBaseTemperature());
        config.property(id("getPrecipitationType"), this.precipitationType::get);
        config.property(id("getTraits"), this.biomeTraits::get);

        config.function(id("is"))
                .param(BIOME_TRAIT)
                .handler(args -> this.hasTrait(args.<BiomeTrait>get(0)));
        config.function(id("isAllOf"))
                .param(BIOME_TRAIT).varParams(BIOME_TRAIT)
                .handler(this::isAllOf);
        config.function(id("isOneOf"))
                .param(BIOME_TRAIT).varParams(BIOME_TRAIT)
                .handler(this::isOneOf);

        for (var trait : BiomeTrait.values())
            config.defineVariable(trait.getName(), () -> this.hasTrait(trait));
    }

    private static BiomeTrait toTrait(Object value) {
        if (value instanceof BiomeTrait trait)
            return trait;
        if (!(value instanceof String name))
            return null;
        var trait = BiomeTrait.of(name);
        if (trait == BiomeTrait.UNKNOWN && !UNKNOWN.equalsIgnoreCase(name))
            return ArgType.reject("unknown biome trait '%s'".formatted(name));
        return trait;
    }

    private boolean isAllOf(final ScriptArguments traits) {
        for (int i = 0; i < traits.count(); i++)
            if (!this.hasTrait(traits.get(i)))
                return false;
        return true;
    }

    private boolean isOneOf(final ScriptArguments traits) {
        for (int i = 0; i < traits.count(); i++)
            if (this.hasTrait(traits.get(i)))
                return true;
        return false;
    }

    private boolean hasTrait(BiomeTrait trait) {
        return this.info != null && this.info.hasTrait(trait);
    }
}