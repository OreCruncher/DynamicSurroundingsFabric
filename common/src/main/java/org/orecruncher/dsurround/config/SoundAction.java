package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * What happens to a sound when it plays, as set in the individual sound configuration.
 */
public enum SoundAction implements StringRepresentable {
    /** Plays normally */
    DEFAULT,
    /** Repeats of the same sound within a short time are dropped */
    CULL,
    /** Never plays */
    BLOCK;

    public static final Codec<SoundAction> CODEC = StringRepresentable.fromEnum(SoundAction::values);

    private final String serializedName = this.name().toLowerCase(Locale.ROOT);

    /**
     * The translation key of the action's name; its help text is under the same key with {@code .help} added.
     */
    public String translationKey() {
        return "dsurround.text.soundconfig." + this.serializedName;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.serializedName;
    }
}
