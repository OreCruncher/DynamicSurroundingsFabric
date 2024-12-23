package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.ISoundFactory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum ItemClassType {
    NONE("none"),
    TOOL("tool"),
    SWORD("sword"),
    SHIELD("shield"),
    AXE("axe"),
    BOW("bow"),
    CROSSBOW("crossbow"),
    POTION("potion"),
    BOOK("book");

    private static final Map<String, ItemClassType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(ItemClassType::getName, (category) -> category));
    public static final Codec<ItemClassType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown item class type"), d -> d.name);

    private final String name;
    private final ResourceLocation toolBarSound;
    private final ResourceLocation swingSound;

    ItemClassType(String name) {
        this.name = name;
        this.toolBarSound = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "toolbar." + name + ".equip");
        this.swingSound = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "toolbar." + name + ".swing");
    }

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);

    public String getName() {
        return this.name;
    }

    public ISoundFactory getToolBarSound() {
        return SOUND_LIBRARY.getSoundFactory(this.toolBarSound).orElseThrow();
    }

    public @Nullable ISoundFactory getSwingSound() {
        return SOUND_LIBRARY.getSoundFactory(this.swingSound).orElse(null);
    }
}
