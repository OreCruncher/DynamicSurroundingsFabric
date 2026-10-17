package org.orecruncher.dsurround.profiling;

import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The recordings /dsprofile writes, in the mod's profiles folder: {@code dsurround-<scene>-<date-time>.jfr}. Only the
 * newest {@link #KEPT} are kept: older ones are deleted as the client starts, with any left half written. Uses nothing
 * from {@code jdk.jfr}, so it runs whatever Java the game has.
 */
public final class ProfileFiles {

    /**
     * How many recordings are kept
     */
    public static final int KEPT = 10;

    private static final String PREFIX = "dsurround-";
    private static final String EXTENSION = ".jfr";
    // A recording as it is first written, before what is private is taken out of it (see ProfileRecording)
    static final String UNFINISHED = ".part";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProfileFiles() {
    }

    /**
     * The name of a recording of a scene made at a time
     */
    public static String name(ProfileScene scene, LocalDateTime when) {
        return PREFIX + scene.name().toLowerCase(Locale.ROOT) + "-" + when.format(STAMP) + EXTENSION;
    }

    /**
     * Deletes all but the newest {@code keep} recordings in {@code folder}, and any left half written (by a game that
     * stopped while writing one). Other files are left alone. Problems are logged, not thrown: it runs as the client
     * starts.
     *
     * @return how many were deleted
     */
    public static int prune(Path folder, int keep, IModLog logger) {
        if (!Files.isDirectory(folder))
            return 0;
        int deleted = 0;
        try (Stream<Path> files = Files.list(folder)) {
            var recordings = files.filter(ProfileFiles::isOurs).sorted(Comparator.comparing(ProfileFiles::modified).reversed()).toList();
            int kept = 0;
            for (var file : recordings) {
                var unfinished = file.getFileName().toString().endsWith(UNFINISHED);
                if (!unfinished && kept < keep) {
                    kept++;
                    continue;
                }
                try {
                    Files.deleteIfExists(file);
                    deleted++;
                } catch (IOException e) {
                    logger.warn("Unable to delete the old profile %s: %s", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to look through the profiles in %s: %s", folder, e);
        }
        if (deleted > 0)
            logger.info("Deleted %d old profile recording(s), keeping the newest %d", deleted, keep);
        return deleted;
    }

    private static boolean isOurs(Path file) {
        var name = file.getFileName().toString();
        return name.startsWith(PREFIX) && (name.endsWith(EXTENSION) || name.endsWith(EXTENSION + UNFINISHED)) && Files.isRegularFile(file);
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
