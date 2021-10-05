package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.biometraits.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.biome.BiomeUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.lang.ref.WeakReference;
import java.util.*;

@Environment(EnvType.CLIENT)
public final class BiomeLibrary {

    private static final Map<Biome, BiomeInfo> biomeInfo = new Reference2ObjectOpenHashMap<>();
    private static WeakReference<Registry<Biome>> biomeRegistry;

    private static final ObjectArray<IBiomeTraitAnalyzer> traitAnalyzer = new ObjectArray<>(16);

    static {
        traitAnalyzer.add(new BiomeCategoryAnalyzer());
        traitAnalyzer.add(new BiomeDeepAnalyzer());
        traitAnalyzer.add(new BiomeExtremeAnalyzer());
        traitAnalyzer.add(new BiomeHillsMountainsAnalyzer());
        traitAnalyzer.add(new BiomePrecipitationAnalyzer());
        traitAnalyzer.add(new BiomeRainfallAnalyzer());
        traitAnalyzer.add(new BiomeSpookyAnalyzer());
        traitAnalyzer.add(new BiomeTempAnalyzer());
        traitAnalyzer.add(new BiomeWasteAnalyzer());

        // Need to know when the biome registry changes.  The actual data for a
        // biome will be lazily generated.
        DynamicRegistrySetupCallback.EVENT.register(registryManager -> {
            // Clear out any data cached prior
            biomeInfo.clear();
            biomeRegistry = new WeakReference<>(registryManager.get(Registry.BIOME_KEY));
            //RegistryEntryAddedCallback.event(biomeRegistry).register(BiomeLibrary::biomeAdded);
        });
    }

    public static void load() {
        biomeInfo.clear();
    }

    public static Registry<Biome> getActiveRegistry() {
        var result = biomeRegistry != null ? biomeRegistry.get() : null;
        return result != null ? result : GameUtils.getRegistryManager().get(Registry.BIOME_KEY);
    }

    @Environment(EnvType.CLIENT)
    public static Identifier getBiomeId(Biome biome) {
        Registry<Biome> biomeRegistry = getActiveRegistry();
        Identifier id = biomeRegistry.getId(biome);
        if (id == null)
            id = BiomeUtils.PLAINS_ID;
        return id;
    }

    @Environment(EnvType.CLIENT)
    public static String getBiomeName(Biome biome) {
        Identifier id = getBiomeId(biome);
        final String fmt = String.format("biome.%s.%s", id.getNamespace(), id.getPath());
        return I18n.translate(fmt);
    }

    public static Collection<String> getBiomeTraits(Biome biome) {
        Identifier id = getBiomeId(biome);
        Set<String> traits = new HashSet<>();

        for (var trait : traitAnalyzer) {
            String result = trait.evaluate(id, biome);
            if (result != null)
                traits.add(result.toUpperCase());
        }

        return traits;
    }

    public static Collection<SoundEvent> findSoundMatches(Biome biome) {
        var info = biomeInfo.get(biome);
        return info != null ? info.findBiomeSoundMatches() : ImmutableList.of();
    }

    public static SoundEvent getRandomSoundAddition(Biome biome, Random rand) {
        var info = biomeInfo.get(biome);
        return info != null ? info.getAdditionalSound(rand) : null;
    }

    /*
    private static void biomeAdded(int i, Identifier identifier, Biome biome) {
        try {
            biomeInfo.put(biome, new BiomeInfo(biome));
        } catch (Exception ex) {
            Client.LOGGER.error(ex, "Unexpected error adding biome %s to map", identifier);
        }
    }
*/
}
