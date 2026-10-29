package org.orecruncher.dsurround.mixinutils;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Features hooked into game code that other mods often replace (the sky, the weather, the fog, particles). Their
 * injectors are {@code require = 0}: if another mod has changed the code so a hook doesn't apply, the feature is lost
 * instead of the game failing to start. Hooks the mod can't work without stay required.
 * <p>
 * Mixin doesn't say whether an optional injector applied, so the class it was applied to is looked at once it is done:
 * a hook applied if the class's own code calls its handler (see {@link #calls}). The mixin plugin
 * keeps the classes our hooks' mixins were applied to ({@link #record}); MixinExtras' injectors are applied after the
 * plugin hears of the class, so it is looked at later, at client start ({@link #check}). In the development
 * environment a hook that didn't apply stops the launch, as a required one would.
 * <p>
 * Free of the game's classes: the mixin plugin uses it while the game's classes are being loaded.
 */
public enum OptionalHook {

    SKY_TINT("Dust storm sky color",
            site("core.MixinSkyRenderer", "dsurround$tintSkyColor")),
    SUNSET_TINT("Dust storm sunrise and sunset color",
            site("core.MixinSkyRenderer", "dsurround$tintSunsetColor")),
    FOG_TINT("Dust storm fog color",
            site("core.MixinFogRenderer", "dsurround$tintFogColor")),
    PRECIPITATION("Particle rain and snow",
            site("core.MixinWeatherEffectRenderer", "dsurround$renderPrecipitation"),
            site("core.MixinLevelRenderer", "dsurround$enterWeatherPass"),
            site("core.MixinLevelRenderer", "dsurround$leaveWeatherPass")),
    GROUND_FOG("Ground fog with Fabulous graphics",
            site("core.MixinLevelRenderer", "dsurround$groundFogAfterWeather")),
    WINDOW_FROST("Window frost without Fabulous graphics",
            site("core.MixinLevelRenderer", "dsurround$frostDepthBeforeTranslucent"),
            site("core.MixinLevelRenderer", "dsurround$frostAfterTranslucentTerrain")),
    STORM_RAIN("Storm strength in vanilla's rain and snow",
            site("core.MixinWeatherEffectRenderer", "dsurround$stormRainLevelDrawn"),
            site("core.MixinClientLevel", "dsurround$stormRainLevelTicked"),
            site("core.MixinAtmosphericFogEnvironment", "dsurround$stormRainFog")),
    RAIN_SOUNDS("Storm rain sounds",
            site("core.MixinClientLevel", "dsurround$rainSound")),
    RAIN_SPLASHES("Hiding vanilla's rain splashes under particle rain",
            site("core.MixinClientLevel", "dsurround$vanillaRainSplash")),
    WIND_BLOWN_PARTICLES("Wind blowing particles",
            site("core.MixinParticleEngine", "dsurround$markWindBlown"),
            site("core.MixinParticle", "dsurround$driftX"),
            site("core.MixinParticle", "dsurround$driftZ")),
    BURNING_ENTITIES("Flames on burning mobs",
            site("core.MixinEntityRenderer", "dsurround$vanillaFire")),
    DEATH_PUFFS("Dark mist when mobs die",
            site("core.MixinLivingEntity", "dsurround$deathPuffs")),
    LIGHTNING_BOLTS("Branching lightning in place of vanilla's bolts",
            site("core.MixinClientLevel", "dsurround$lightningBolt"),
            site("core.MixinLightningBoltRenderer", "dsurround$vanillaBolt"),
            site("core.MixinLevelRenderer", "dsurround$lightningAfterWeather")),
    DYNAMIC_CLOUDS("Dynamic clouds in place of vanilla's",
            site("core.MixinCloudRenderer", "dsurround$clouds")),
    PRECIPITATION_AHEAD("Building rain and snow while the world is drawn",
            site("core.MixinGameRenderer", "dsurround$beginLevel")),
    FRAME_TIMES("Frame times in profile recordings",
            site("core.MixinMinecraftClient", "dsurround$timeFrame"));

    /**
     * Whether a hook applied
     */
    public enum Status {
        APPLIED,
        /** Its handler isn't called: another mod changed the code it hooks */
        MISSING,
        /** The class it hooks wasn't seen (not loaded yet, or the plugin's record isn't visible here) */
        UNKNOWN
    }

    /**
     * A handler in one of the mod's mixins
     *
     * @param mixin   the mixin's class name, after the mixins package
     * @param handler the handler method's name
     * @param or      another handler that does instead, or null: where the loaders' code differs (NeoForge draws
     *                particles through a method of its own), each has a handler of its own, and either being called
     *                means the hook applied
     */
    record Site(String mixin, String handler, String or) {
        String mixinClassName() {
            return MIXIN_PACKAGE + this.mixin;
        }

        /**
         * Its handler, and the other one if it has one
         */
        List<String> handlers() {
            return this.or == null ? List.of(this.handler) : List.of(this.handler, this.or);
        }
    }

    private static final String MIXIN_PACKAGE = "org.orecruncher.dsurround.mixins.";
    // In the names of the methods MixinExtras adds
    private static final String MIXINEXTRAS = "$mixinextras$";

    // The classes our hooks' mixins were applied to, by mixin; emptied once checked
    private static final Map<String, ClassNode> TARGETS = new ConcurrentHashMap<>();
    // Set by check(); null until then
    private static volatile Map<OptionalHook, Status> statuses;

    private final String feature;
    private final List<Site> sites;

    OptionalHook(String feature, Site... sites) {
        this.feature = feature;
        this.sites = List.of(sites);
    }

    private static Site site(String mixin, String handler) {
        return new Site(mixin, handler, null);
    }


    /**
     * What is lost when it doesn't apply, for the log and diagnostics
     */
    public String feature() {
        return this.feature;
    }

    /**
     * The translation key of the feature, for chat
     */
    public String translationKey() {
        return "dsurround.hook." + this.name().toLowerCase(Locale.ROOT);
    }

    List<Site> sites() {
        return this.sites;
    }

    /**
     * Whether it applied, as found at client start; UNKNOWN before then
     */
    public Status status() {
        var current = statuses;
        return current == null ? Status.UNKNOWN : current.get(this);
    }

    /**
     * A line saying it is off and why, if it didn't apply
     */
    public Optional<String> describe() {
        return this.status() == Status.MISSING
                ? Optional.of("%s: off (the game code it hooks was changed by another mod)".formatted(this.feature))
                : Optional.empty();
    }

    /**
     * Called by the mixin plugin once one of the mod's mixins is applied to a class. The class is kept, if one of the
     * hooks is in that mixin, until {@link #check}: more is injected into it after this.
     */
    static void record(String mixinClassName, ClassNode target) {
        if (statuses != null)
            return;
        for (var hook : values())
            for (var site : hook.sites)
                if (site.mixinClassName().equals(mixinClassName)) {
                    TARGETS.put(mixinClassName, target);
                    return;
                }
    }

    /**
     * Looks at whether each hook applied, logs those that didn't, and lets go of the classes kept for it. Call once
     * the classes the hooks are in are loaded.
     *
     * @param development whether this is the development environment, where a hook that didn't apply is an error
     * @throws IllegalStateException in development, if a hook didn't apply
     */
    public static void check(IModLog logger, boolean development) {
        var found = new EnumMap<OptionalHook, Status>(OptionalHook.class);
        for (var hook : values())
            found.put(hook, statusOf(hook, TARGETS::get));
        statuses = found;
        TARGETS.clear();

        found.forEach((hook, status) -> logger.debug("Optional hook %s: %s", hook, status));
        var missing = found.entrySet().stream().filter(e -> e.getValue() == Status.MISSING).map(Map.Entry::getKey).toList();
        for (var hook : missing)
            hook.describe().ifPresent(line -> logger.warn("%s", line));
        if (found.values().stream().allMatch(s -> s == Status.UNKNOWN))
            logger.warn("Unable to tell whether the optional game hooks applied");
        if (development && !missing.isEmpty())
            throw new IllegalStateException("Optional game hooks didn't apply: " + missing);
    }

    /**
     * Whether a hook applied, from the classes its mixins were applied to (by mixin class name): MISSING if any of its
     * handlers isn't called, else UNKNOWN if any of its classes wasn't seen
     */
    static Status statusOf(OptionalHook hook, Function<String, ClassNode> targets) {
        var status = Status.APPLIED;
        for (var site : hook.sites) {
            var target = targets.apply(site.mixinClassName());
            if (target == null)
                status = Status.UNKNOWN;
            else if (site.handlers().stream().noneMatch(handler -> calls(target, handler)))
                return Status.MISSING;
        }
        return status;
    }

    /**
     * Whether the class's own code calls the handler: by its name, as Mixin renamed it (a prefix, then {@code $} and
     * the name), or through the bridge MixinExtras makes for a handler with sugar ({@code @Local}). The bridge calls
     * the handler whether or not the injection found where to go, so calls from MixinExtras' methods don't count.
     */
    static boolean calls(ClassNode target, String handler) {
        var renamed = "$" + handler;
        var bridge = renamed + MIXINEXTRAS;
        for (var method : target.methods) {
            if (method.name.contains(MIXINEXTRAS))
                continue;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call
                        && call.owner.equals(target.name)
                        && (call.name.equals(handler) || call.name.endsWith(renamed) || call.name.contains(bridge)))
                    return true;
            }
        }
        return false;
    }
}
