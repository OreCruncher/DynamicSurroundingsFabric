package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IndividualSoundControlList extends AbstractSelectionList<IndividualSoundControlListEntry> {

    private final boolean enablePlay;
    private final ISoundLibrary soundLibrary;
    private int width;
    private List<IndividualSoundConfigEntry> source;
    private String lastSearchText = null;

    public IndividualSoundControlList(final Screen parent, final Minecraft mcIn, int widthIn, int heightIn, int topIn, int slotWidth, int slotHeightIn, boolean enablePlay, final Supplier<String> filter, @Nullable final IndividualSoundControlList oldList) {
        super(mcIn, widthIn, heightIn, topIn, slotHeightIn);

        this.soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        this.enablePlay = enablePlay;
        this.width = slotWidth;

        // Things like resizing will cause reconstruction and this preserves the existing state
        if (oldList != null) {
            this.source = oldList.source;
        }

        // Initialize the first pass
        this.setSearchFilter(filter);
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    public void setRowWidth(int width) {
        this.width = width;
        this.children().forEach(c -> {
            c.setX(this.getRowLeft());
            c.setWidth(this.width);
        });
    }

    public void setSearchFilter(final Supplier<String> filterBy) {
        final String filter = filterBy.get();

        if (this.lastSearchText != null && this.lastSearchText.equals(filter))
            return;

        this.lastSearchText = filter;

        // Clear any existing children - they are going to be repopulated
        this.clearEntries();

        // Load up sources if needed
        if (this.source == null) {
            this.source = new ArrayList<>(this.getSortedSoundConfigurations());
        }

        Predicate<IndividualSoundConfigEntry> process;

        // An empty filter matches everything
        if (StringUtils.isEmpty(filter)) {
            process = (isc) -> true;
        } else {
            // Try compiling as a regular expression. If it fails just treat as a normal contains.
            try {
                var pattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
                process = (isc) -> pattern.matcher(isc.soundEventIdProjected).find();
            } catch (Throwable t) {
                process = (isc) -> isc.soundEventIdProjected.contains(filter);
            }
        }

        IndividualSoundControlListEntry first = null;
        for (IndividualSoundConfigEntry cfg : this.source) {
            if (process.test(cfg)) {
                final IndividualSoundControlListEntry entry = new IndividualSoundControlListEntry(cfg, this.enablePlay);
                if (first == null)
                    first = entry;
                this.addEntry(entry);
            }
        }

        if (first != null)
            this.setFocused(first);
    }

    @Nullable
    public IndividualSoundControlListEntry getEntryAt(final int mouseX, final int mouseY) {
        return this.getEntryAtPosition(mouseX, mouseY);
    }

    public void tick() {
        this.children().forEach(IndividualSoundControlListEntry::tick);
    }

    // Gathers all the sound configs that are different from default for handling.
    protected Collection<IndividualSoundConfigEntry> getConfigs() {
        final List<IndividualSoundConfigEntry> configs = new ArrayList<>();
        for (final IndividualSoundConfigEntry cfg : this.source) {
            if (cfg.isNotDefault())
                configs.add(cfg);
        }
        return configs;
    }

    public void saveChanges() {
        this.soundLibrary.saveIndividualSoundConfigs(getConfigs());
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput builder) {
        // Narrate my shiny metal...
    }

    protected Collection<IndividualSoundConfigEntry> getSortedSoundConfigurations() {

        final Map<Identifier, IndividualSoundConfigEntry> map = new HashMap<>();

        // Get a list of all registered sounds.  We don't use the vanilla registries since
        // we will have more sounds than are registered.
        for (final SoundEvent event : this.soundLibrary.getRegisteredSoundEvents()) {
            IndividualSoundConfigEntry entry = IndividualSoundConfigEntry.createDefault(event);
            map.put(entry.soundEventId, entry);
        }

        // Override with the defaults from configuration.  Make a copy of the original, so it doesn't change.
        for (IndividualSoundConfigEntry entry : this.soundLibrary.getIndividualSoundConfigs()) {
            map.put(entry.soundEventId, entry);
        }

        return map.values().stream().sorted(IndividualSoundConfigEntry::compareTo).collect(Collectors.toList());
    }
}