package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.effects.blocks.producers.*;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.effects.particles.FireflyParticle;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public enum BlockEffectType {
    UNKNOWN("unknown", (chance, condition) -> null),
    STEAM_COLUMN("steam_column", SteamColumnProducer::new),
    FLAME_JET("fire_jet", FlameJetProducer::new),
    BUBBLE_COLUMN("bubble_column", UnderwaterBubbleProducer::new),
    WATERFALL("waterfall", WaterSplashProducer::new),
    FIREFLY("firefly",
            (chance, conditions) -> new BlockParticleEffectProducer(chance, conditions,
                    (world, state, pos, rand) -> new FireflyParticle(world, pos.getX() + 0.D, pos.getY() + 0.5D, pos.getZ() + 0.5D)));

    private static final Map<String, BlockEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(BlockEffectType::getName, (category) -> category));
    public static final Codec<BlockEffectType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown block effect type"), d -> d.name);

    private final String name;
    private final BiFunction<Script, Script, IBlockEffectProducer> producer;
    private BooleanSupplier enabled;

    BlockEffectType(String name, BiFunction<Script, Script, IBlockEffectProducer> producerFactory) {
        this.name = name;
        this.producer = producerFactory;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    public Optional<IBlockEffectProducer> createInstance(Script chance, Script conditions) {
        if (this.isEnabled())
            return Optional.ofNullable(this.producer.apply(chance, conditions));
        return Optional.empty();
    }

    private void setConfigProvider(BooleanSupplier supplier) {
        this.enabled = supplier;
    }

    public static BlockEffectType byName(String name) {
        return BY_NAME.get(name);
    }

    static {
        Configuration.BlockEffects config = ContainerManager.resolve(Configuration.BlockEffects.class);
        UNKNOWN.setConfigProvider(() -> false);
        STEAM_COLUMN.setConfigProvider(() -> config.steamColumnEnabled);
        FLAME_JET.setConfigProvider(() -> config.flameJetEnabled);
        BUBBLE_COLUMN.setConfigProvider(() -> config.bubbleColumnEnabled);
        WATERFALL.setConfigProvider(() -> config.waterfallsEnabled);
        FIREFLY.setConfigProvider(() -> config.firefliesEnabled);
    }
}
