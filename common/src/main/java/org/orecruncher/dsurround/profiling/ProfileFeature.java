package org.orecruncher.dsurround.profiling;

import org.orecruncher.dsurround.lib.config.ConfigElement;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.mixinutils.OptionalHook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One thing a recording's times depend on besides the machine, written into it once (see {@link FeatureEvent}): whether
 * an optional game hook applied, whether a feature stands aside for another mod, or a setting's value. Nothing in it
 * names the player: the hooks, features and settings are the mod's own, and a mod a feature stands aside for is one of
 * those it knows. Text settings are left out, as what a player types could be anything. Uses nothing from
 * {@code jdk.jfr}.
 *
 * @param kind  {@link #HOOK}, {@link #CONFLICT} or {@link #SETTING}
 * @param name  the hook's or feature's name, or the setting's path in the config ({@code weatherOptions.enableDustStorms})
 * @param state what it is: the hook's status, "on" or "off (mod installed)", or the setting's value
 * @param usual whether it is as it is out of the box: the hook applied, the feature not standing aside, the setting at
 *              its default
 */
record ProfileFeature(String kind, String name, String state, boolean usual) {

    static final String HOOK = "hook";
    static final String CONFLICT = "conflict";
    static final String SETTING = "setting";

    /**
     * Each optional hook's status, each feature that can stand aside for another mod, and each setting
     *
     * @param hooks    whether a hook applied
     * @param blocking the id of the installed mod a feature stands aside for, if one is
     */
    static List<ProfileFeature> of(ConfigurationData config, Function<OptionalHook, OptionalHook.Status> hooks,
                                   Function<FeatureConflict, Optional<String>> blocking) {
        var features = new ArrayList<ProfileFeature>();
        for (var hook : OptionalHook.values()) {
            var status = hooks.apply(hook);
            features.add(new ProfileFeature(HOOK, hook.name(), status.name(), status == OptionalHook.Status.APPLIED));
        }
        for (var conflict : FeatureConflict.values()) {
            var mod = blocking.apply(conflict);
            features.add(new ProfileFeature(CONFLICT, conflict.name(), mod.map("off (%s installed)"::formatted).orElse("on"), mod.isEmpty()));
        }
        settings(config.getSpecification(), config, "", features);
        return features;
    }

    /**
     * As the game has them now: on the render thread, after the optional hooks were checked
     */
    static List<ProfileFeature> capture(ConfigurationData config) {
        return of(config, OptionalHook::status, conflict -> conflict.blockingMod().flatMap(mod -> mod.installedId()));
    }

    private static void settings(Collection<ConfigElement<?>> elements, Object instance, String path, List<ProfileFeature> features) {
        for (var element : elements) {
            var name = path + element.fieldName();
            if (element instanceof ConfigElement.PropertyGroup group) {
                settings(group.getChildren(), group.getInstance(instance), name + ".", features);
            } else if (element instanceof ConfigElement.PropertyValue<?> value && !(element instanceof ConfigElement.StringValue)) {
                var current = value.getValue(instance);
                features.add(new ProfileFeature(SETTING, name, String.valueOf(current), Objects.equals(current, value.defaultValue())));
            }
        }
    }
}
