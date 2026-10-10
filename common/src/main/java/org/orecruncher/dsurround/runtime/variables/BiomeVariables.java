package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.biome.IBiomeIdentity;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.function.CachingSupplier;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.ScriptArguments;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

import java.util.function.Function;

public final class BiomeVariables extends VariableSet {

    private static final String UNKNOWN = "UNKNOWN";

    /**
     * A biome trait name, case-insensitive. Constant names are checked when the script is compiled, so a typo
     * such as biome.is('HOTT') is reported as an error instead of never matching.
     */
    private static final ArgType<BiomeTrait> BIOME_TRAIT = ArgType.of("biome trait", BiomeVariables::toTrait);

    // Vanilla's Biome.warmEnoughToRain() threshold
    private static final float RAIN_TEMPERATURE = 0.15F;

    private final IBiomeLibrary biomeLibrary;
    // Where the player is in game; the biome's own for biome rules
    private final CachingSupplier<String> precipitationType;
    private final CachingSupplier<String> id = CachingSupplier.from(() -> this.info == null ? UNKNOWN : this.info.getBiomeId().toString());
    private final CachingSupplier<String> biomeTraits = CachingSupplier.from(() -> this.info == null ? "[]" : this.info.getTraits().toString());

    private Biome biome;
    private IBiomeIdentity info;

    /**
     * The player's biome, updated each tick. Precipitation is at the player's position.
     */
    public BiomeVariables(IBiomeLibrary biomeLibrary) {
        this(biomeLibrary, biome -> GameUtils.getPlayer()
                .map(p -> biome.getPrecipitationAt(p.blockPosition(), p.level().getSeaLevel()))
                .orElse(Biome.Precipitation.NONE));
    }

    BiomeVariables(IBiomeLibrary biomeLibrary, Function<Biome, Biome.Precipitation> precipitation) {
        super("biome");
        this.biomeLibrary = biomeLibrary;
        this.precipitationType = CachingSupplier.from(() ->
                (this.biome == null ? Biome.Precipitation.NONE : precipitation.apply(this.biome)).name());
    }

    /**
     * Variables for biome rules, which describe a biome rather than where the player is. Precipitation is the
     * biome's own (see {@link #biomePrecipitation}), so a rule gives the same answer wherever the player happens
     * to be when the libraries reload, or at the main menu.
     */
    public static BiomeVariables forBiomeRules(IBiomeLibrary biomeLibrary) {
        return new BiomeVariables(biomeLibrary, BiomeVariables::biomePrecipitation);
    }

    /**
     * What falls in the biome itself, from its own settings, the way vanilla decides at sea level. Doesn't call
     * Biome.getPrecipitationAt(): seasons mods change that to read the current world, which there may not be.
     */
    static Biome.Precipitation biomePrecipitation(Biome biome) {
        if (!biome.hasPrecipitation())
            return Biome.Precipitation.NONE;
        return biome.getBaseTemperature() >= RAIN_TEMPERATURE ? Biome.Precipitation.RAIN : Biome.Precipitation.SNOW;
    }

    @Override
    public void tick() {
        Biome newBiome = null;
        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            newBiome = player.level().getBiome(player.getOnPos()).value();
        }
        this.setBiome(newBiome);
        // Depends on where the player is, not only the biome, so it can change while the biome stays the same
        this.precipitationType.clear();
    }

    public void setBiome(final Biome biome) {
        if (biome != null) {
            var info = this.biomeLibrary.getBiomeInfo(biome);
            this.setBiome(biome, info);
        } else {
            this.setBiome(null, null);
        }
    }

    public void setBiome(final Biome biome, final IBiomeIdentity info) {
        // Usually the same as last tick. A reload gives the same biome a new info, so both are compared.
        if (biome == this.biome && info == this.info)
            return;
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
        config.property(id("getPrecipitationType"), this::getPrecipitationType);
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

    String getPrecipitationType() {
        return this.precipitationType.get();
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