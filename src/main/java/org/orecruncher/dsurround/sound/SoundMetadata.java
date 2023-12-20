package org.orecruncher.dsurround.sound;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class SoundMetadata {

    private final Text title;
    private final Text caption;
    private final List<Text> credits;

    public SoundMetadata() {
        this.title = Text.empty();
        this.caption = Text.empty();
        this.credits = ImmutableList.of();
    }

    public SoundMetadata(final SoundMetadataConfig cfg) {
        Objects.requireNonNull(cfg);

        this.title = cfg.title().map(Text::translatable).orElse(Text.empty());
        this.caption = cfg.caption().map(Text::translatable).orElse(Text.empty());

        if (cfg.credits() == null || cfg.credits().isEmpty()) {
            this.credits = ImmutableList.of();
        } else {
            this.credits = new ArrayList<>();
            for (final String s : cfg.credits()) {
                if (StringUtils.isEmpty(s))
                    this.credits.add(Text.empty());
                else
                    this.credits.add(Text.of(s));
            }
        }
    }

    /**
     * Gets the title configured in sounds.json, or EMPTY if not present.
     *
     * @return Configured title, or EMPTY if not present.
     */
    public Text getTitle() {
        return this.title;
    }

    /**
     * Gets the caption (subtitle) configured in sounds.json, or EMPTY if not present.
     *
     * @return Configured caption, or EMPTY if not present.
     */
    public Text getCaption() {
        return this.caption;
    }

    /**
     * Gets the credits configured for the sound event in sounds.json, or an empty list if not present.
     *
     * @return List containing 0 or more strings describing the sound credits.
     */
    public List<Text> getCredits() {
        return this.credits;
    }
}