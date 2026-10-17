package org.orecruncher.dsurround.profiling;

import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The recordings /dsprofile writes, in the mod's profiles folder: {@code dsurround-<scene>-<date-time>.zip}, holding
 * the recording ({@code .jfr}) of the same name. Zipped, a recording is a third the size, and can be attached to an
 * issue on GitHub, which takes zips but not recordings. Only the newest {@link #KEPT} are kept: older ones are deleted
 * as the client starts, with any left half written. Uses nothing from {@code jdk.jfr}, so it runs whatever Java the game
 * has.
 */
public final class ProfileFiles {

    /**
     * How many recordings are kept
     */
    public static final int KEPT = 10;

    private static final String PREFIX = "dsurround-";
    private static final String EXTENSION = ".jfr";
    private static final String ZIPPED = ".zip";
    // A recording as it is first written, before what is private is taken out of it (see ProfileRecording), and a zip
    // as it is written
    static final String UNFINISHED = ".part";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProfileFiles() {
    }

    /**
     * The name of a recording of a scene made at a time, before it is zipped (see {@link #zip})
     */
    public static String name(ProfileScene scene, LocalDateTime when) {
        return PREFIX + scene.name().toLowerCase(Locale.ROOT) + "-" + when.format(STAMP) + EXTENSION;
    }

    /**
     * Zips a finished recording into a zip of the same name beside it, and deletes the recording. The zip is written
     * under another name first, so a game stopped while writing it leaves no zip that looks finished.
     *
     * @return the zip
     */
    public static Path zip(Path recording) throws IOException {
        var name = recording.getFileName().toString();
        var stem = name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
        var zip = recording.resolveSibling(stem + ZIPPED);
        var unfinished = recording.resolveSibling(stem + ZIPPED + UNFINISHED);
        try {
            try (var out = new ZipOutputStream(Files.newOutputStream(unfinished))) {
                var entry = new ZipEntry(name);
                entry.setLastModifiedTime(Files.getLastModifiedTime(recording));
                out.putNextEntry(entry);
                Files.copy(recording, out);
                out.closeEntry();
            }
            Files.move(unfinished, zip, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(unfinished);
        }
        Files.delete(recording);
        return zip;
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
        if (name.endsWith(UNFINISHED))
            name = name.substring(0, name.length() - UNFINISHED.length());
        // A recording left unzipped (by a game stopped before zipping it) counts too
        return name.startsWith(PREFIX) && (name.endsWith(ZIPPED) || name.endsWith(EXTENSION)) && Files.isRegularFile(file);
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
