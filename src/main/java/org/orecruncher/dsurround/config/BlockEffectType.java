package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringIdentifiable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.blocks.producers.FlameJetProducer;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.effects.blocks.producers.SteamColumnProducer;
import org.orecruncher.dsurround.effects.blocks.producers.UnderwaterBubbleProducer;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public enum BlockEffectType implements StringIdentifiable {
    UNKNOWN("unknown", (chance, condition) -> null, () -> false),
    STEAM_COLUMN("steam_column", SteamColumnProducer::new, () -> Client.Config.blockEffects.steamColumnEnabled),
    FLAME_JET("fire_jet", FlameJetProducer::new, () -> Client.Config.blockEffects.flameJetEnabled),
    BUBBLE_COLUMN("bubble_column", UnderwaterBubbleProducer::new, () -> Client.Config.blockEffects.bubbleColumnEnabled);

    private static final Map<String, BlockEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(BlockEffectType::getName, (category) -> category));
    public static final Codec<BlockEffectType> CODEC = StringIdentifiable.createCodec(BlockEffectType::values, BlockEffectType::byName);

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

    @Override
    public String asString() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled.get();
    }

    public Optional<IBlockEffectProducer> getInstance(Script chance, Script conditions) {
        if (this.isEnabled())
            return Optional.ofNullable(producer.apply(chance, conditions));
        return Optional.empty();
    }

    public static BlockEffectType byName(String name) {
        return BY_NAME.get(name);
    }
}
