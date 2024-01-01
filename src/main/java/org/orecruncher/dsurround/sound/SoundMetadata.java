package org.orecruncher.dsurround.sound;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SoundMetadata {

    private final Component title;
    private final Component caption;
    private final List<Credit> credits;

    public SoundMetadata() {
        this.title = Component.empty();
        this.caption = Component.empty();
        this.credits = ImmutableList.of();
    }

    public SoundMetadata(final SoundMetadataConfig cfg) {
        Objects.requireNonNull(cfg);

        this.title = cfg.title().map(Component::translatable).orElse(Component.empty());
        this.caption = cfg.caption().map(Component::translatable).orElse(Component.empty());

        if (cfg.credits() == null || cfg.credits().isEmpty()) {
            this.credits = ImmutableList.of();
        } else {
            var temp = new ArrayList<Credit>(cfg.credits().size());
            for (var entry : cfg.credits()) {
                var name = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.name()));
                var author = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.author()));
                var license = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.license()));
                var creditEntry = new Credit(name, author, license);
                temp.add(creditEntry);
            }
            this.credits = ImmutableList.copyOf(temp);
        }
    }

    public record Credit(Component name, Component author, Component license) {

    }

    /**
     * Gets the title configured in sounds.json, or EMPTY if not present.
     *
     * @return Configured title, or EMPTY if not present.
     */
    public Component getTitle() {
        return this.title;
    }

    /**
     * Gets the caption (subtitle) configured in sounds.json, or EMPTY if not present.
     *
     * @return Configured caption, or EMPTY if not present.
     */
    public Component getCaption() {
        return this.caption;
    }

    /**
     * Gets the credits configured for the sound event in sounds.json, or an empty list if not present.
     *
     * @return List containing zero or more strings describing the sound credits.
     */
    public List<Credit> getCredits() {
        return this.credits;
    }
}