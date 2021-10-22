package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public enum ItemClassType implements StringIdentifiable {
    NONE("none", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.utility.equip")))
            .category(SoundCategory.PLAYERS).volume(0.3F).build()),
    TOOL("tool", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.tool.equip")))
            .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    SWORD("sword", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.sword.equip")))
            .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    SHIELD("shield", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.shield.equip")))
            .category(SoundCategory.PLAYERS).volume(0.25F).pitchRange(0.8F, 1.2F).build()),
    AXE("axe", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.blunt.equip")))
            .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    BOW("bow", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.bow.equip")))
            .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    CROSSBOW("crossbow", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.bow.equip")))
            .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build()),
    POTION("potion", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "item.potion.equip")))
            .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build()),
    BOOK("book", SoundFactoryBuilder
            .create(new SoundEvent(new Identifier(Client.ModId, "pageflip")))
            .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build());

    private static final Map<String, ItemClassType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(ItemClassType::getName, (category) -> category));
    public static final Codec<ItemClassType> CODEC = StringIdentifiable.createCodec(ItemClassType::values, ItemClassType::byName);

    private final String name;
    private final ISoundFactory toolBarSound;

    ItemClassType(String name, ISoundFactory toolBarSound) {
        this.name = name;
        this.toolBarSound = toolBarSound;
    }

    public String getName() {
        return this.name;
    }

    public ISoundFactory getToolBarSound() {
        return this.toolBarSound;
    }

    public static ItemClassType byName(String name) {
        return BY_NAME.get(name);
    }

    @Override
    public String asString() {
        return this.name;
    }
}
