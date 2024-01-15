package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
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
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.utility.equip")))
                .category(SoundSource.PLAYERS).volume(0.3F).build(),
            null),
    TOOL("tool",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.tool.equip")))
                .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.tool.swing")))
                    .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build()),
    SWORD("sword",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.sword.equip")))
                .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.sword.swing")))
                    .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build()),
    SHIELD("shield",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.shield.equip")))
                .category(SoundSource.PLAYERS).volume(0.25F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.shield.equip")))
                    .category(SoundSource.PLAYERS).volume(0.25F).pitch(0.4F, 0.6F).build()),
    AXE("axe",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.blunt.equip")))
                .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.blunt.swing")))
                    .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build()),
    BOW("bow",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.bow.equip")))
                .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.tool.swing")))
                    .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build()),
    CROSSBOW("crossbow",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.bow.equip")))
                .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.blunt.swing")))
                    .category(SoundSource.PLAYERS).volume(0.5F).pitch(0.8F, 1.2F).build()),
    POTION("potion",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.potion.equip")))
                .category(SoundSource.PLAYERS).volume(0.8F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.potion.equip")))
                    .category(SoundSource.PLAYERS).volume(1F).pitch(0.8F, 1.2F).build()),
    BOOK("book",
            SoundFactoryBuilder
                .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "pageflip")))
                .category(SoundSource.PLAYERS).volume(0.8F).pitch(0.8F, 1.2F).build(),
            SoundFactoryBuilder
                    .create(SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "pageflipheavy")))
                    .category(SoundSource.PLAYERS).volume(0.8F).pitch(0.8F, 1.2F).build());

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
