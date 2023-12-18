package org.orecruncher.dsurround.config.libraries.impl;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Guard;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.runtime.BiomeConditionEvaluator;
import org.orecruncher.dsurround.xface.IBiomeExtended;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public final class BiomeLibrary implements IBiomeLibrary {

    private static final String FILE_NAME = "biomes.json";
    private static final Codec<List<BiomeConfigRule>> CODEC = Codec.list(BiomeConfigRule.CODEC);

    private final IModLog logger;

    private final Map<InternalBiomes, BiomeInfo> internalBiomes = new EnumMap<>(InternalBiomes.class);

    // Cached list of biome config rules.  Need to hold onto them
    // because they may be needed to handle dynamic biome load.
    private final ObjectArray<BiomeConfigRule> biomeConfigs = new ObjectArray<>(64);

    // Current version of the configs that are loaded.  Used to detect when
    // configs changed and cached biome info needs a refresh.
    private int version = 0;

    public BiomeLibrary(IModLog logger) {
        this.logger = logger;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
        // Wipe out the internal biome cache.  These will be reset.
        this.internalBiomes.clear();
        this.biomeConfigs.clear();

        var accessors = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), FILE_NAME);

        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                this.biomeConfigs.addAll(cfg);
        });

        this.version++;

        for (var b : InternalBiomes.values())
            initializeInternalBiome(b);

        this.logger.info("%d biome configs loaded; version is now %d", biomeConfigs.size(), version);
    }

    private void initializeInternalBiome(InternalBiomes biome) {
        String match = "@" + biome.getName();
        var info = new BiomeInfo(this.version, biome.getId(), biome.getName(), biome.getTraits());

        for (var c : this.biomeConfigs) {
            if (c.biomeSelector.asString().equalsIgnoreCase(match)) {
                info.update(c);
            }
        }

        this.internalBiomes.put(biome, info);
    }

    private static Registry<Biome> getActiveRegistry() {
        return GameUtils.getRegistryManager().get(RegistryKeys.BIOME);
    }

    public static Biome getBiome(Identifier biomeId) {
        return getActiveRegistry().get(biomeId);
    }

    @Override
    public BiomeInfo getBiomeInfo(Biome biome) {
        // check the cached property on the biome and return the info
        // that is there.
        var info = ((IBiomeExtended) (Object) biome).getInfo();
        if (info != null && info.getVersion() == this.version)
            return info;

        // Not set or something changed.  Need a refresh.
        Identifier id;
        String name;

        // Pull from cached data if we have it, otherwise lookup
        if (info != null) {
            id = info.getBiomeId();
            name = info.getBiomeName();
        } else {
            id = getBiomeId(biome);
            name = getBiomeName(id);
        }

        // Regenerate the traits.  Something about the biome may have changed
        // which could ripple into traits.
        BiomeTraits traits = BiomeTraits.createFrom(id, biome);

        // Build out the info object and store into the biome.  We need to do that
        // so that when applying configs the script engine can find it.
        final var result = new BiomeInfo(this.version, id, name, traits);
        ((IBiomeExtended) (Object) biome).setInfo(result);

        // Apply rule configs
        Guard.execute(() -> applyRuleConfigs(biome, result));
        return result;
    }

    @Override
    public BiomeInfo getBiomeInfo(InternalBiomes biome) {
        return this.internalBiomes.get(biome);
    }

    private void applyRuleConfigs(Biome biome, BiomeInfo info) {
        for (var c : this.biomeConfigs) {
            // Skip internal definitions - they are handled elsewhere and
            // do not apply to regular Minecraft biomes
            if (c.biomeSelector.asString().startsWith("@"))
                continue;

            try {
                var applies = BiomeConditionEvaluator.INSTANCE.check(biome, c.biomeSelector);
                if (applies) {
                    try {
                        info.update(c);
                    } catch (final Throwable t) {
                        this.logger.warn("Unable to process biome sound configuration [%s]", c.toString());
                    }
                }
            } catch (Throwable t) {
                this.logger.error(t, "Unexpected error processing biome %s", info.getBiomeId());
            }
        }

        // Reduce memory consumption as much as possible
        info.trim();
    }

    private static Identifier getBiomeId(Biome biome) {
        RegistryKey<Biome> key = getActiveRegistry().getKey(biome).orElse(BiomeKeys.THE_VOID);
        return key.getValue();
    }

    @Override
    public String getBiomeName(Identifier id) {
        final String fmt = String.format("biome.%s.%s", id.getNamespace(), id.getPath());
        return I18n.translate(fmt);
    }

    @Override
    public Stream<String> dump() {
        return getActiveRegistry()
                .stream()
                .map(this::getBiomeInfo)
                .map(BiomeInfo::toString)
                .sorted();
    }
}
