package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class IndividualSoundControlList extends EntryListWidget<IndividualSoundControlListEntry> {

    private final Screen parent;
    private final boolean enablePlay;
    private final int width;
    private List<IndividualSoundConfigEntry> source;
    private String lastSearchText = null;

    public IndividualSoundControlList(final Screen parent, final MinecraftClient mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotWidth, int slotHeightIn, boolean enablePlay, final Supplier<String> filter, @Nullable final IndividualSoundControlList oldList) {
        super(mcIn, widthIn, heightIn, /* topIn,*/ bottomIn, slotHeightIn);

        this.parent = parent;
        this.enablePlay = enablePlay;
        this.width = slotWidth;

        // Things like resizing will cause reconstruction and this preserves the existing state
        if (oldList != null)
            this.source = oldList.source;

        // Initialize the first pass
        this.setSearchFilter(filter, false);
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
            this.source = new ArrayList<>(SoundLibraryHelpers.getSortedSoundConfigurations());

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
        Client.SoundConfig.saveIndividualSoundConfigs(getConfigs());
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Narrate my shiny metal...
    }
}