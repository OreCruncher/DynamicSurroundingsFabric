package org.orecruncher.dsurround.sound;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SoundMetadata {

    private final Component title;
    private final Component subTitle;
    private final List<Credit> credits;
    private final SoundSource category;
    private final boolean isDefault;

    /**
     * Metadata for a sound with none configured.
     */
    public SoundMetadata() {
        this(SoundSource.AMBIENT, true);
    }

    /**
     * Metadata with nothing configured but the category, estimated from the sound's ID.
     */
    public SoundMetadata(ResourceLocation location) {
        this(estimateSoundSource(location), false);
    }

    private SoundMetadata(SoundSource category, boolean isDefault) {
        this.title = Component.empty();
        this.subTitle = Component.empty();
        this.credits = ImmutableList.of();
        this.category = category;
        this.isDefault = isDefault;
    }

    public SoundMetadata(ResourceLocation location, SoundMetadataConfig cfg) {
        Objects.requireNonNull(cfg);

        this.isDefault = false;

        this.title = cfg.title().map(Component::translatable).orElse(Component.empty());
        this.subTitle = cfg.subtitle().map(Component::translatable).orElse(Component.empty());

        if (cfg.credits() == null || cfg.credits().isEmpty()) {
            this.credits = ImmutableList.of();
        } else {
            var temp = new ArrayList<Credit>(cfg.credits().size());
            for (var entry : cfg.credits()) {
                var name = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.name()));
                var author = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.author()));
                var webSite = entry.website().map(website -> Component.nullToEmpty(ChatFormatting.stripFormatting(website)));
                var license = Component.nullToEmpty(ChatFormatting.stripFormatting(entry.license()));
                var creditEntry = new Credit(name, author, webSite, license);
                temp.add(creditEntry);
            }
            this.credits = ImmutableList.copyOf(temp);
        }

        this.category = cfg.category().orElseGet(() -> estimateSoundSource(location));
    }

    public boolean isDefault() {
        return this.isDefault;
    }

    private static SoundSource estimateSoundSource(ResourceLocation location) {
        var path = location.getPath();
        if (path.startsWith("music"))
            return SoundSource.MUSIC;
        if (path.startsWith("block"))
            return SoundSource.BLOCKS;
        if (path.startsWith("entity") && path.endsWith("step"))
            return SoundSource.NEUTRAL;
        if (path.startsWith("entity"))
            return SoundSource.HOSTILE;
        if (path.startsWith("weather"))
            return SoundSource.WEATHER;
        // Including "ambient" sounds
        return SoundSource.AMBIENT;
    }

    public record Credit(Component name, Component author, Optional<Component> webSite, Component license) {

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
     * Gets the subtitle (subtitle) configured in sounds.json, or EMPTY if not present.
     *
     * @return Configured subtitle, or EMPTY if not present.
     */
    public Component getSubTitle() {
        return this.subTitle;
    }

    /**
     * True if a title is configured and it isn't blank in the current language.
     */
    public boolean hasTitle() {
        return !this.title.getString().isBlank();
    }

    /**
     * True if a subtitle is configured and it isn't blank in the current language.
     */
    public boolean hasSubTitle() {
        return !this.subTitle.getString().isBlank();
    }

    /**
     * Gets the credits configured for the sound event in sounds.json, or an empty list if not present.
     *
     * @return List containing zero or more strings describing the sound credits.
     */
    public List<Credit> getCredits() {
        return this.credits;
    }

    /**
     * Gets the sound category that has been configured or estimated from the location ID.
     */
    public SoundSource getCategory() {
        return this.category;
    }
}