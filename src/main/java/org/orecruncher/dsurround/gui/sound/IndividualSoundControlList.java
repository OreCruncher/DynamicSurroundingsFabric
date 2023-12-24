package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IndividualSoundControlList extends EntryListWidget<IndividualSoundControlListEntry> {

    private final Screen parent;
    private final boolean enablePlay;
    private final int width;
    private final ISoundLibrary soundLibrary;
    private List<IndividualSoundConfigEntry> source;
    private String lastSearchText = null;

    public IndividualSoundControlList(final Screen parent, final MinecraftClient mcIn, int widthIn, int heightIn, int topIn, int slotWidth, int slotHeightIn, boolean enablePlay, final Supplier<String> filter, @Nullable final IndividualSoundControlList oldList) {
        super(mcIn, widthIn, heightIn, topIn, slotHeightIn);

        this.soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        this.parent = parent;
        this.enablePlay = enablePlay;
        this.width = slotWidth;

        // Things like resizing will cause reconstruction and this preserves the existing state
        if (oldList != null)
            this.source = oldList.source;

        // Initialize the first pass
        this.setSearchFilter(filter, false);

        this.setRenderBackground(false);
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    @Override
    protected int getScrollbarPositionX() {
        return (this.parent.width + this.getRowWidth()) / 2 + 20;
    }

    public void setSearchFilter(final Supplier<String> filterBy, final boolean forceReload) {
        final String filter = filterBy.get();

        if (!forceReload && this.lastSearchText != null && this.lastSearchText.equals(filter))
            return;

        this.lastSearchText = filter;

        // Clear any existing children - they are going to be repopulated
        this.clearEntries();

        // Load up source if needed
        if (this.source == null || forceReload)
            this.source = new ArrayList<>(this.getSortedSoundConfigurations());

        // Get the filter string.  It's a simple contains check.
        final Function<IndividualSoundConfigEntry, Boolean> process;

        if (filter == null || filter.isEmpty()) {
            process = (isc) -> true;
        } else {
            process = (isc) -> isc.soundEventId.toString().contains(filter);
        }

        IndividualSoundControlListEntry first = null;
        for (IndividualSoundConfigEntry cfg : this.source) {
            if (process.apply(cfg)) {
                final IndividualSoundControlListEntry entry = new IndividualSoundControlListEntry(cfg, this.enablePlay);
                if (first == null)
                    first = entry;
                this.addEntry(entry);
            }
        }

        if (first != null)
            this.ensureVisible(first);
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
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
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