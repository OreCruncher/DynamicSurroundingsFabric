package org.orecruncher.dsurround.lib.platform;

import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import static org.orecruncher.dsurround.lib.platform.KnownMod.*;

/**
 * The mod's features that stand aside for other mods: each is off while any of its mods is installed. Kept in one
 * place so the log and the diagnostics overlay can say what is off and why.
 * <p>
 * These are decided in code rather than by skipping mixins in {@code DSurroundMixinPlugin}: a feature rarely maps to
 * a whole mixin, and code can say why it is off. The plugin is for when another mod's mixin conflicts with ours.
 */
public enum FeatureConflict {

    /**
     * Sound Physics Remastered does its own sound processing
     */
    ENHANCED_SOUNDS("Enhanced sound processing", SOUND_PHYSICS_REMASTERED),
    /**
     * Mods that draw rain and snow as particles of their own
     */
    PARTICLE_WEATHER("Particle rain, snow and dust storms", PARTICLE_RAIN, SIMPLE_WEATHER),
    /**
     * Particle Rain makes its own ripples. Simple Weather's drops splash as vanilla's do, so the mod's ripples still
     * take their place on water.
     */
    RAIN_RIPPLES("Water ripples", PARTICLE_RAIN),
    /**
     * The {@code discardNonClientSoundPlays} option is ignored while Raise Sound Limit Simplified is installed
     */
    OFF_THREAD_SOUND_DISCARD("Discarding sounds played off the client thread", RAISE_SOUND_LIMIT_SIMPLIFIED),
    /**
     * Presence Footsteps' own footstep sounds take their place
     */
    FOOTSTEP_EFFECTS("Wet surface accents and brush step effect", PRESENCE_FOOTSTEPS),
    /**
     * Quilt runs Fabric mods, but not the client commands the mod registers
     */
    CLIENT_COMMANDS("Client commands", QUILTED_LOADER);

    private final String feature;
    private final List<KnownMod> mods;
    // Asked once, when first wanted; null until then
    private Optional<KnownMod> blockingMod;

    FeatureConflict(String feature, KnownMod... mods) {
        this.feature = feature;
        this.mods = List.of(mods);
    }

    /**
     * What is turned off, for the log and diagnostics
     */
    public String feature() {
        return this.feature;
    }

    /**
     * The translation key of what is turned off, for chat
     */
    public String translationKey() {
        return "dsurround.conflict." + this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The mods it stands aside for
     */
    public List<KnownMod> mods() {
        return this.mods;
    }

    /**
     * The installed mod it stands aside for, if there is one
     */
    public Optional<KnownMod> blockingMod() {
        var mod = this.blockingMod;
        if (mod == null)
            this.blockingMod = mod = this.blockingMod(KnownMod::isInstalled);
        return mod;
    }

    /**
     * The installed mod it stands aside for, as {@code isInstalled} says: the first of its mods that is
     */
    Optional<KnownMod> blockingMod(Predicate<KnownMod> isInstalled) {
        return this.mods.stream().filter(isInstalled).findFirst();
    }

    /**
     * Whether it is off because one of its mods is installed
     */
    public boolean isBlocked() {
        return this.blockingMod().isPresent();
    }

    /**
     * A line saying it is off and why, if it is
     */
    public Optional<String> describe() {
        return this.blockingMod().map(mod -> "%s: off (%s installed)".formatted(this.feature, mod.installedId().orElseThrow()));
    }

    /**
     * Logs each feature that is off, and why
     */
    public static void logBlocked(IModLog logger) {
        for (var conflict : values())
            conflict.describe().ifPresent(line -> logger.info("%s", line));
    }
}
