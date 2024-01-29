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

    public SoundMetadata() {
        this.title = Component.empty();
        this.subTitle = Component.empty();
        this.credits = ImmutableList.of();
        this.category = SoundSource.AMBIENT;
        this.isDefault = true;
    }

    public SoundMetadata(ResourceLocation location) {
        this.title = Component.empty();
        this.subTitle = Component.empty();
        this.credits = ImmutableList.of();
        this.isDefault = false;
        this.category = this.estimateSoundSource(location);
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

        this.category = cfg.category().orElseGet(() -> this.estimateSoundSource(location));
    }

    public boolean isDefault() {
        return this.isDefault;
    }

    private SoundSource estimateSoundSource(ResourceLocation location) {
        var path = location.getPath();
        if (path.startsWith("music"))
            return SoundSource.MUSIC;
        if (path.startsWith("block"))
            return SoundSource.BLOCKS;
        if (path.startsWith("entity"))
            return SoundSource.HOSTILE;
        if (path.startsWith("weather"))
            return SoundSource.WEATHER;
        if (path.startsWith("ambient"))
            return SoundSource.AMBIENT;
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