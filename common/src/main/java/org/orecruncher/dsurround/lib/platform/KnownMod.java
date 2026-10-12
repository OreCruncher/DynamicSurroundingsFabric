package org.orecruncher.dsurround.lib.platform;

import dev.architectury.platform.Platform;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Other mods Dynamic Surroundings changes what it does for: turning a feature off that they provide, or working with
 * them. A mod known by more than one id (a fork, or a different id on each loader) is one constant, installed if any of
 * its ids is.
 * <p>
 * Whether each is installed is asked of the loader the first time it is wanted, then kept: the loader knows its mods
 * from the start, so this can be asked at any time, even from a mixin's static field. Ids only known at run time (a
 * resource folder's namespace, a script's argument) are asked of the loader directly instead.
 */
public enum KnownMod {

    /**
     * Its own footstep sounds take the place of the mod's step accents and brush step effect
     */
    PRESENCE_FOOTSTEPS(true, "presencefootsteps"),
    /**
     * Does its own sound processing, so the mod's enhanced sound processing is turned off
     */
    SOUND_PHYSICS_REMASTERED(true, "sound_physics_remastered"),
    /**
     * Draws rain and snow as particles, so the mod's particle rain, snow, dust storms and rain ripples stand aside
     */
    PARTICLE_RAIN(true, "particlerain"),
    /**
     * Draws rain and snow as particles (NeoForge only), so the mod's particle rain, snow and dust storms stand aside.
     * Its drops splash as vanilla's do, so the mod's water ripples still take their place on water.
     */
    SIMPLE_WEATHER(true, "simple_weather"),
    /**
     * Raises the sound limit itself, so the mod doesn't discard sounds to stay under it
     */
    RAISE_SOUND_LIMIT_SIMPLIFIED(true, "rsls"),
    /**
     * The config screen library; its id differs between Fabric and NeoForge
     */
    CLOTH_CONFIG(true, "cloth-config", "cloth_config"),
    YACL(true, "yet_another_config_lib_v3"),
    /**
     * Runs Fabric mods on NeoForge
     */
    SINYTRA_CONNECTOR(true, "connectormod"),
    /**
     * Seasons, which change temperatures and so whether it rains or snows
     */
    SERENE_SEASONS(true, "sereneseasons"),
    /**
     * Quilt runs Fabric mods, but not the client commands the mod registers
     */
    QUILTED_LOADER(true, "quilt_loader"),
    /**
     * Opens the config screen itself, so the mod's own key for it is left unbound
     */
    MODMENU(false, "modmenu"),
    /**
     * Shader packs, which replace the drawing that the mod's own shaders fit into. Oculus is its NeoForge fork.
     */
    IRIS(true, "iris", "oculus");

    private final boolean showInDiagnostics;
    private final List<String> ids;
    // Asked once, when first wanted; null until then
    private Optional<String> installedId;

    KnownMod(boolean showInDiagnostics, String... ids) {
        this.showInDiagnostics = showInDiagnostics;
        this.ids = List.of(ids);
    }

    /**
     * The mod's ids, the first being its usual one
     */
    public List<String> ids() {
        return this.ids;
    }

    /**
     * Whether it is listed among the mods of interest in the diagnostics overlay
     */
    public boolean showInDiagnostics() {
        return this.showInDiagnostics;
    }

    /**
     * Whether the mod is installed, under any of its ids
     */
    public boolean isInstalled() {
        return this.installedId().isPresent();
    }

    /**
     * The id it is installed under, if it is
     */
    public Optional<String> installedId() {
        var id = this.installedId;
        if (id == null)
            this.installedId = id = this.installedId(Platform::isModLoaded);
        return id;
    }

    /**
     * The id it is installed under, as {@code isModLoaded} says: the first of its ids that is
     */
    Optional<String> installedId(Predicate<String> isModLoaded) {
        return this.ids.stream().filter(isModLoaded).findFirst();
    }

    /**
     * The mods that draw rain and snow as particles of their own, for which the mod's particle weather stands aside
     */
    public static final List<KnownMod> PARTICLE_WEATHER = List.of(PARTICLE_RAIN, SIMPLE_WEATHER);

    /**
     * An installed mod that draws rain and snow as particles of its own, if there is one: the mod's particle rain,
     * snow and dust storms are off, and left to it
     */
    public static Optional<KnownMod> particleWeatherMod() {
        for (var mod : PARTICLE_WEATHER)
            if (mod.isInstalled())
                return Optional.of(mod);
        return Optional.empty();
    }
}
