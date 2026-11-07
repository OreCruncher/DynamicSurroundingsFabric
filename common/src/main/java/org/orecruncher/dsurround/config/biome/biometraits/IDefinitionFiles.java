// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.resources.IModFiles;
import org.orecruncher.dsurround.lib.resources.ModFiles;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads the world generation files mods ship in their jars: {@code data/<namespace>/worldgen/<folder>/<path>.json}.
 * A mod that adds biomes has to be installed on the client too, so its files are there whatever server is played on;
 * biomes only a server's datapack has, or a mod builds in code, have none.
 */
@FunctionalInterface
public interface IDefinitionFiles {

    /**
     * The file, parsed, if there is one and it reads
     *
     * @param folder the folder under {@code worldgen}: {@code biome}, {@code placed_feature}, {@code feature}
     */
    Optional<JsonElement> read(String namespace, String folder, String path);

    static String resource(String namespace, String folder, String path) {
        return "data/" + namespace + "/worldgen/" + folder + "/" + path + ".json";
    }

    /**
     * The files in the installed mods (Minecraft's own included): those of the mod whose id is the namespace, else of
     * the first mod that has the namespace's data (see {@link ModFiles#ownerOf})
     */
    static IDefinitionFiles installedMods() {
        return (namespace, folder, path) -> ModFiles.ownerOf(namespace)
                .flatMap(owner -> parse(ContainerManager.resolve(IModFiles.class), owner, resource(namespace, folder, path)));
    }

    /**
     * The files under the roots a namespace's are in
     */
    static IDefinitionFiles under(Function<String, List<Path>> rootsOf) {
        var files = new ModFiles.PathBased(rootsOf);
        return (namespace, folder, path) -> parse(files, namespace, resource(namespace, folder, path));
    }

    /**
     * The files on a class loader's class path, as the game's jar and test resources are
     */
    static IDefinitionFiles classPath(ClassLoader loader) {
        return (namespace, folder, path) -> {
            try (var in = loader.getResourceAsStream(resource(namespace, folder, path))) {
                if (in == null)
                    return Optional.empty();
                return Optional.of(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        };
    }

    private static Optional<JsonElement> parse(IModFiles files, String modId, String file) {
        if (!files.has(modId, file))
            return Optional.empty();
        try (var reader = new InputStreamReader(files.open(modId, file), StandardCharsets.UTF_8)) {
            return Optional.of(JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
