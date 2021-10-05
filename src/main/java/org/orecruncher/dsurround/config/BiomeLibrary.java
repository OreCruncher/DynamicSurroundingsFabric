package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.biome.Biome;

import java.util.Collection;
import java.util.Map;
import java.util.Random;

@Environment(EnvType.CLIENT)
public final class BiomeLibrary {

    private static final Map<Biome, BiomeInfo> biomeInfo = new Reference2ObjectOpenHashMap<>();

    public static void load() {
        biomeInfo.clear();
    }

    public static Collection<SoundEvent> findSoundMatches(Biome biome) {
        return ImmutableList.of();
    }

    public static SoundEvent getRandomSoundAddition(Biome biome, Random rand) {
        return null;
    }
}
