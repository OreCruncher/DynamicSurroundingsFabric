package org.orecruncher.dsurround.sound;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SoundMetadata {

    private final Component title;
    private final Component caption;
    private final List<Component> credits;

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
            this.credits = new ArrayList<>();
            for (final String s : cfg.credits()) {
                if (StringUtils.isEmpty(s))
                    this.credits.add(Component.empty());
                else
                    this.credits.add(Component.literal(s));
            }
        }
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
     * @return List containing 0 or more strings describing the sound credits.
     */
    public List<Component> getCredits() {
        return this.credits;
    }
}