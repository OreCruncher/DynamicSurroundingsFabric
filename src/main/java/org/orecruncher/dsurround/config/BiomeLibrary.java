package org.orecruncher.dsurround.config;

import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Guard;
import org.orecruncher.dsurround.lib.biome.BiomeUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.validation.ListValidator;
import org.orecruncher.dsurround.lib.validation.Validators;
import org.orecruncher.dsurround.runtime.BiomeConditionEvaluator;
import org.orecruncher.dsurround.xface.IBiomeExtended;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public final class BiomeLibrary {

    private static final IModLog LOGGER = Client.LOGGER.createChild(BiomeLibrary.class);
    private static final Type biomeType = TypeToken.getParameterized(List.class, BiomeConfigRule.class).getType();

    // Cached list of biome config rules.  Need to hold onto them
    // because they may be needed to handle dynamic biome load.
    private static Collection<BiomeConfigRule> biomeConfigs;

    // Current version of the configs that are loaded.  Used to detect when
    // configs changed and cached biome info needs a refresh.
    private static int version = 0;

    static {
        // Validator for the json
        Validators.registerValidator(biomeType, new ListValidator<BiomeConfigRule>());

        // Log when the registry changes as to understand the log context better
        DynamicRegistrySetupCallback.EVENT.register(registryManager -> LOGGER.info("Biome registry reload detected"));
    }

    public static void load() {
        ObjectArray<BiomeConfigRule> configs = new ObjectArray<>(64);
        var accessors = ResourceUtils.findConfigs(Client.ModId, Client.DATA_PATH.toFile(), "biomes.json");

        for (var accessor : accessors) {
            var config = accessor.<List<BiomeConfigRule>>as(biomeType);
            configs.addAll(config);
        }

        biomeConfigs = configs;
        version++;

        LOGGER.info("%d biome configs loaded; version is now %d", biomeConfigs.size(), version);
    }

    private static Registry<Biome> getActiveRegistry() {
        return GameUtils.getRegistryManager().get(Registry.BIOME_KEY);
    }

    public static BiomeInfo getBiomeInfo(Biome biome) {
        // check the cached property on the biome and return the info
        // that is there.
        var info = ((IBiomeExtended) (Object) biome).getInfo();
        if (info != null && info.getVersion() == version)
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
        final var result = new BiomeInfo(version, id, name, traits);
        ((IBiomeExtended) (Object) biome).setInfo(result);

        // Apply rule configs
        Guard.execute(() -> applyRuleConfigs(biome, result));
        return result;
    }

    private static void applyRuleConfigs(Biome biome, BiomeInfo info) {
        for (var c : biomeConfigs) {
            try {
                var applies = BiomeConditionEvaluator.INSTANCE.check(biome, c.biomeSelector);
                if (applies) {
                    try {
                        info.update(c);
                    } catch (final Throwable t) {
                        LOGGER.warn("Unable to process biome sound configuration [%s]", c.toString());
                    }
                }
            } catch(Exception ignore) {
                int x = 0;
            }
        }

        // Reduce memory consumption as much as possible
        info.trim();
    }

    static Identifier getBiomeId(Biome biome) {
        Registry<Biome> biomeRegistry = getActiveRegistry();
        Identifier id = biomeRegistry.getId(biome);
        if (id == null)
            id = BiomeUtils.DEFAULT_ID;
        return id;
    }

    public static String getBiomeName(Identifier id) {
        final String fmt = String.format("biome.%s.%s", id.getNamespace(), id.getPath());
        return I18n.translate(fmt);
    }

    public static Collection<SoundEvent> findBiomeSoundMatches(Biome biome) {
        var info = getBiomeInfo(biome);
        return info.findBiomeSoundMatches();
    }

    public static SoundEvent getExtraSound(Biome biome, SoundEventType type, Random rand) {
        var info = getBiomeInfo(biome);
        return info.getExtraSound(type, rand);
    }

    public static Stream<String> dumpBiomes() {
        return getActiveRegistry()
                .stream()
                .map(BiomeLibrary::getBiomeInfo)
                .map(BiomeInfo::toString)
                .sorted();
    }
}
