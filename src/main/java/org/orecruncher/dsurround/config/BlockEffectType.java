package org.orecruncher.dsurround.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.blocks.producers.*;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.effects.particles.FireflyParticle;
import org.orecruncher.dsurround.lib.scripting.Script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public enum BlockEffectType {
    UNKNOWN("unknown",
            (chance, condition) -> null,
            () -> false),
    STEAM_COLUMN("steam_column",
            SteamColumnProducer::new,
            () -> Client.Config.blockEffects.steamColumnEnabled),
    FLAME_JET("fire_jet",
            FlameJetProducer::new,
            () -> Client.Config.blockEffects.flameJetEnabled),
    BUBBLE_COLUMN("bubble_column",
            UnderwaterBubbleProducer::new,
            () -> Client.Config.blockEffects.bubbleColumnEnabled),
    WATERFALL("waterfall",
            WaterSplashProducer::new,
            () -> Client.Config.blockEffects.waterfallsEnabled),
    FIREFLY("firefly",
            (chance, conditions) -> new BlockParticleEffectProducer(chance, conditions,
                    (world, state, pos, rand) -> new FireflyParticle(world, pos.getX() + 0.D, pos.getY() + 0.5D, pos.getZ() + 0.5D)),
            () -> Client.Config.blockEffects.firefliesEnabled);

    private static final Map<String, BlockEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(BlockEffectType::getName, (category) -> category));
    public static final Codec<BlockEffectType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown block effect type"), d -> d.name);

    private final String name;
    private final BiFunction<Script, Script, IBlockEffectProducer> producer;
    private final Supplier<Boolean> enabled;

    BlockEffectType(String name, BiFunction<Script, Script, IBlockEffectProducer> producerFactory, Supplier<Boolean> enabled) {
        this.name = name;
        this.producer = producerFactory;
        this.enabled = enabled;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled.get();
    }

    public Optional<IBlockEffectProducer> createInstance(Script chance, Script conditions) {
        if (this.isEnabled())
            return Optional.ofNullable(this.producer.apply(chance, conditions));
        return Optional.empty();
    }

    public static BlockEffectType byName(String name) {
        return BY_NAME.get(name);
    }
}
