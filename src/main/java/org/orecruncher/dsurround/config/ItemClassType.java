package org.orecruncher.dsurround.config;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum ItemClassType {
    NONE("none",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.utility.equip")))
                .category(SoundCategory.PLAYERS).volume(0.3F).build(),
            null),
    TOOL("tool",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.tool.equip")))
                .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.tool.swing")))
                    .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    SWORD("sword",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.sword.equip")))
                .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.sword.swing")))
                    .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    SHIELD("shield",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.shield.equip")))
                .category(SoundCategory.PLAYERS).volume(0.25F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.shield.equip")))
                    .category(SoundCategory.PLAYERS).volume(0.25F).pitchRange(0.4F, 0.6F).build()),
    AXE("axe",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.blunt.equip")))
                .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.blunt.swing")))
                    .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    BOW("bow",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.bow.equip")))
                .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.tool.swing")))
                    .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    CROSSBOW("crossbow",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.bow.equip")))
                .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.blunt.swing")))
                    .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    POTION("potion",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "item.potion.equip")))
                .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "item.potion.equip")))
                    .category(SoundCategory.PLAYERS).volume(1F).pitchRange(0.8F, 1.2F).build()),
    BOOK("book",
            SoundFactoryBuilder
                .create(SoundEvent.of(new Identifier(Client.ModId, "pageflip")))
                .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.of(new Identifier(Client.ModId, "pageflipheavy")))
                    .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build());

    private static final Map<String, ItemClassType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(ItemClassType::getName, (category) -> category));
    public static final Codec<ItemClassType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown item class type"), d -> d.name);

    private final String name;
    private final ISoundFactory toolBarSound;
    private final @Nullable ISoundFactory swingSound;

    ItemClassType(String name, ISoundFactory toolBarSound, @Nullable ISoundFactory swingSound) {
        this.name = name;
        this.toolBarSound = toolBarSound;
        this.swingSound = swingSound;
    }

    public String getName() {
        return this.name;
    }

    public ISoundFactory getToolBarSound() {
        return this.toolBarSound;
    }

    public @Nullable ISoundFactory getSwingSound() {
        return this.swingSound;
    }

    public static ItemClassType byName(String name) {
        return BY_NAME.get(name);
    }
}
