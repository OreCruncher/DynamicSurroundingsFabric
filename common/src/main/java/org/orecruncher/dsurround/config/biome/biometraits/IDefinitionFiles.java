package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.orecruncher.dsurround.lib.resources.ModRoots;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
     * @param folder the folder under {@code worldgen}: {@code biome}, {@code placed_feature}, {@code configured_feature}
     */
    Optional<JsonElement> read(String namespace, String folder, String path);

    static String resource(String namespace, String folder, String path) {
        return "data/" + namespace + "/worldgen/" + folder + "/" + path + ".json";
    }

    /**
     * The files in the installed mods' jars (Minecraft's own included): those of the mod whose id is the namespace,
     * else of the first mod that has the namespace's data (see {@link ModRoots})
     */
    static IDefinitionFiles installedMods() {
        return under(ModRoots::forNamespace);
    }

    /**
     * The files under the roots a namespace's are in
     */
    static IDefinitionFiles under(Function<String, List<Path>> rootsOf) {
        // Where a namespace's files are, found once
        Map<String, List<Path>> roots = new ConcurrentHashMap<>();
        return (namespace, folder, path) -> ModRoots.find(roots.computeIfAbsent(namespace, rootsOf), resource(namespace, folder, path))
                .flatMap(IDefinitionFiles::parse);
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

    private static Optional<JsonElement> parse(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Optional.of(JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
