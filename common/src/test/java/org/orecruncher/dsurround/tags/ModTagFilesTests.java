package org.orecruncher.dsurround.tags;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mod's tags are read from dsconfigs/tags in any mod's assets, so a tag with no file, or a reference to a
 * misspelled tag, quietly matches nothing. These check that every tag the code declares, and every tag the
 * configuration refers to, has a file.
 */
public class ModTagFilesTests {

    static {
        // The tag classes reach the game's registries; first, before the fields below use them
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Traits the mod works out in code (placeholders, and those of the synthetic biomes for the player, villages,
    // being underwater and so on), never from a tag; their tags have no file
    private static final Set<BiomeTrait> DETECTED_IN_CODE = EnumSet.of(BiomeTrait.UNKNOWN, BiomeTrait.NONE,
            BiomeTrait.SYNTHETIC, BiomeTrait.INSIDE, BiomeTrait.VILLAGE, BiomeTrait.PLAYER, BiomeTrait.SPACE,
            BiomeTrait.CLOUDS, BiomeTrait.UNDER_RIVER, BiomeTrait.UNDER_WATER, BiomeTrait.UNDER_OCEAN);

    private static final Pattern MOD_TAG_REFERENCE = Pattern.compile("\"#" + Constants.MOD_ID + ":([a-z0-9_/]+)\"");

    private static Path assets;
    // Each tag file, as "registry folder/tag path" (e.g. "tags/block/effects/fireflies"), from any mod's dsconfigs
    private static Set<String> tagFiles;

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        // Found from a file of this mod's own: other "assets" folders are on the class path too
        var lang = Objects.requireNonNull(ModTagFilesTests.class.getResource("/assets/" + Constants.MOD_ID + "/lang/en_us.json"));
        assets = Path.of(lang.toURI()).getParent().getParent().getParent();
        tagFiles = new HashSet<>();
        try (var files = Files.walk(assets)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                var relative = assets.relativize(p).toString().replace('\\', '/');
                var marker = "/dsconfigs/";
                var at = relative.indexOf(marker);
                if (at >= 0 && relative.startsWith("tags/", at + marker.length()))
                    tagFiles.add(relative.substring(at + marker.length(), relative.length() - ".json".length()));
            });
        }
    }

    @Test
    void foundTheTagFiles() {
        assertTrue(tagFiles.size() > 50, "only found " + tagFiles.size() + " tag files");
    }

    @Test
    void everyDeclaredTagHasAFile() {
        var exempt = new HashSet<>();
        DETECTED_IN_CODE.forEach(t -> exempt.add(t.getBiomeTag()));

        var missing = new TreeSet<String>();
        for (var tag : ModTags.getModTags()) {
            if (!tag.location().getNamespace().equals(Constants.MOD_ID) || exempt.contains(tag))
                continue;
            var file = Registries.tagsDirPath(tag.registry()) + "/" + tag.location().getPath();
            if (!tagFiles.contains(file))
                missing.add(file);
        }
        assertTrue(missing.isEmpty(), "tags declared in code with no file: " + missing);
    }

    @Test
    void everyTagReferenceInTheConfigurationHasAFile() throws IOException {
        var missing = new ArrayList<String>();
        try (var files = Files.walk(assets)) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var relative = assets.relativize(path).toString().replace('\\', '/');
                var registryFolder = registryFolderFor(relative);
                if (registryFolder == null)
                    continue;
                var matcher = MOD_TAG_REFERENCE.matcher(Files.readString(path));
                while (matcher.find()) {
                    var file = registryFolder + "/" + matcher.group(1);
                    if (!tagFiles.contains(file))
                        missing.add(relative + " -> #" + Constants.MOD_ID + ":" + matcher.group(1));
                }
            }
        }
        assertTrue(missing.isEmpty(), "tag references with no tag file: " + missing);
    }

    @Test
    void conventionTagsAreReferencedAsTags() throws IOException {
        // The "c" namespace only holds tags, so an entry there without its "#" names a biome or block that can't
        // exist, and quietly adds nothing (as dsurround:is_outer_end_island once did)
        var plain = Pattern.compile("\"(c:[a-z0-9_/]+)\"");
        var missing = new ArrayList<String>();
        try (var files = Files.walk(assets)) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var relative = assets.relativize(path).toString().replace('\\', '/');
                if (!relative.contains("/dsconfigs/tags/"))
                    continue;
                var matcher = plain.matcher(Files.readString(path));
                while (matcher.find())
                    missing.add(relative + " -> " + matcher.group(1));
            }
        }
        assertTrue(missing.isEmpty(), "convention tags referenced without '#': " + missing);
    }

    /**
     * Which registry's tags a config file refers to: a tag file refers to tags of its own registry; the block and
     * sound mapping configuration to block tags. Null for files that don't refer to tags.
     */
    private static String registryFolderFor(String relativePath) {
        var marker = "/dsconfigs/";
        var at = relativePath.indexOf(marker);
        if (at < 0)
            return null;
        var inConfig = relativePath.substring(at + marker.length());
        if (inConfig.startsWith("tags/")) {
            var parts = inConfig.split("/");
            // tags/block/..., or tags/worldgen/biome/...
            return parts[1].equals("worldgen") ? "tags/worldgen/" + parts[2] : "tags/" + parts[1];
        }
        if (List.of("blocks.json", "sound_mappings.json").contains(inConfig))
            return "tags/block";
        return null;
    }
}
