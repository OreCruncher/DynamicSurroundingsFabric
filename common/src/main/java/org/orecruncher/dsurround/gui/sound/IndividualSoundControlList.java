package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One row per sound. The rows are built once; the search filter only changes which of them are shown, so their
 * widgets (and any sound a row is playing) survive filtering.
 */
public class IndividualSoundControlList extends ContainerObjectSelectionList<IndividualSoundControlListEntry> implements AutoCloseable {

    private final ISoundLibrary soundLibrary;
    private final List<IndividualSoundControlListEntry> allEntries;
    private int rowWidth;
    private @Nullable String lastSearchText = null;

    public IndividualSoundControlList(final Minecraft mc, int width, int height, int y, int itemHeight, boolean enablePlay) {
        super(mc, width, height, y, itemHeight);

        this.soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
        this.allEntries = this.getSortedSoundConfigurations().stream()
                .map(cfg -> new IndividualSoundControlListEntry(cfg, enablePlay))
                .toList();
        this.rowWidth = width;

        this.setSearchFilter("");
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    /**
     * The narrowest the rows can be without any row's controls running past its right edge.
     */
    public int getMinimumRowWidth() {
        return this.allEntries.stream().mapToInt(IndividualSoundControlListEntry::getMinimumWidth).max().orElse(0);
    }

    public void setRowWidth(int width) {
        this.rowWidth = width;
        this.allEntries.forEach(e -> e.setWidth(width));
    }

    /**
     * Shows only the sounds whose id matches the filter. The filter is tried as a case-insensitive regular
     * expression first; if it isn't a valid one, it is matched as plain text, also ignoring case.
     */
    public void setSearchFilter(@Nullable String filter) {
        if (filter == null)
            filter = "";

        if (filter.equals(this.lastSearchText))
            return;

        this.lastSearchText = filter;

        Predicate<IndividualSoundConfigEntry> matches;
        if (filter.isEmpty()) {
            matches = cfg -> true;
        } else {
            try {
                var pattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
                matches = cfg -> pattern.matcher(cfg.soundEventIdProjected).find();
            } catch (PatternSyntaxException e) {
                var text = filter.toLowerCase(Locale.ROOT);
                matches = cfg -> cfg.soundEventIdProjected.toLowerCase(Locale.ROOT).contains(text);
            }
        }

        final var test = matches;
        this.replaceEntries(this.allEntries.stream().filter(e -> test.test(e.getData())).toList());
        this.setScrollAmount(0);
    }

    /**
     * The row under the mouse, or null if the mouse isn't over a row.
     */
    @Nullable
    public IndividualSoundControlListEntry getEntryAt(final int mouseX, final int mouseY) {
        return this.isMouseOver(mouseX, mouseY) ? this.getEntryAtPosition(mouseX, mouseY) : null;
    }

    /**
     * Ticks every row, including hidden ones, so a sound that finishes while its row is filtered out still resets
     * the row's play button.
     */
    public void tick() {
        this.allEntries.forEach(IndividualSoundControlListEntry::tick);
    }

    /**
     * Stops any sound started by a row.
     */
    @Override
    public void close() {
        this.allEntries.forEach(IndividualSoundControlListEntry::close);
    }

    public void saveChanges() {
        // Only configurations that differ from the default need to be saved
        var configs = this.allEntries.stream()
                .map(IndividualSoundControlListEntry::getData)
                .filter(IndividualSoundConfigEntry::isNotDefault)
                .toList();
        this.soundLibrary.saveIndividualSoundConfigs(configs);
    }

    private Collection<IndividualSoundConfigEntry> getSortedSoundConfigurations() {

        final Map<ResourceLocation, IndividualSoundConfigEntry> map = new HashMap<>();

        // Get a list of all registered sounds.  We don't use the vanilla registries since
        // we will have more sounds than are registered.
        for (final SoundEvent event : this.soundLibrary.getRegisteredSoundEvents()) {
            IndividualSoundConfigEntry entry = IndividualSoundConfigEntry.createDefault(event);
            map.put(entry.soundEventId, entry);
        }

        // Override with the current configuration. Copies are edited, so Cancel leaves the live configuration as it was.
        for (IndividualSoundConfigEntry entry : this.soundLibrary.getIndividualSoundConfigs()) {
            map.put(entry.soundEventId, IndividualSoundConfigEntry.from(entry));
        }

        return map.values().stream().sorted(IndividualSoundConfigEntry::compareTo).toList();
    }
}
