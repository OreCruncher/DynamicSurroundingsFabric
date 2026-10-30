package org.orecruncher.dsurround.config;

import com.google.gson.JsonElement;
import net.minecraft.util.ExtraCodecs;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.resources.DiscoveredResource;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the config presets (see {@link ConfigPreset}) from every dsconfigs/presets.json, in the mod's own assets,
 * resource packs and the config folder's configs/(namespace)/, and hands them to the configuration. Ours come first;
 * a modpack's file adds presets, replaces one by its id, or hides one. A value a preset can't apply, or that presets
 * may not change, is logged and left out, keeping the rest of the preset.
 */
public final class PresetLoader {

    private static final String FILE_NAME = "presets.json";

    /**
     * Groups no preset may change: Scary Content is a comfort choice that a preset must not turn back on.
     */
    private static final List<String> OFF_LIMITS = List.of("scaryContent.");

    private final Configuration config;
    private final IModLog logger;

    public PresetLoader(Configuration config, IModLog logger) {
        this.config = config;
        this.logger = ModLog.createChild(logger, "Presets");
    }

    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (scope == IReloadEvent.Scope.TAGS)
            return;
        var files = resourceUtilities.findModResources(ExtraCodecs.JSON, FILE_NAME);
        var presets = load(this.config, files, this.logger::warn);
        this.config.setPresets(presets);
        this.logger.info("%d presets loaded", presets.size());
    }

    /**
     * The presets from the files, merged and checked against the configuration. Each problem found is described to
     * {@code problems}, naming the namespace whose file it is in.
     */
    public static List<ConfigPreset> load(Configuration config, Collection<DiscoveredResource<JsonElement>> files, Consumer<String> problems) {
        // Files in our namespace first (the mod's own, then resource packs', then the config folder's), so the others
        // can replace or hide ours; the resource manager lists namespaces alphabetically
        var ordered = new ArrayList<>(files);
        ordered.sort(Comparator.comparing(file -> !file.namespace().equals(Constants.MOD_ID)));

        var parsed = new ArrayList<List<ConfigPreset>>();
        for (var file : ordered) {
            Consumer<String> report = problem -> problems.accept("[" + file.namespace() + "] " + FILE_NAME + ": " + problem);
            var presets = new ArrayList<ConfigPreset>();
            for (var preset : ConfigPreset.parse(file.resourceContent(), report))
                presets.add(preset.hidden() ? preset : preset.validated(config, PresetLoader::allowed, report));
            parsed.add(presets);
        }
        return ConfigPreset.merge(parsed);
    }

    /**
     * Whether presets may change the property at a dotted path.
     */
    static boolean allowed(String path) {
        return OFF_LIMITS.stream().noneMatch(path::startsWith);
    }
}
