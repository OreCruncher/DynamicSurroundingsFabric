package org.orecruncher.dsurround.config.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.config.biome.AcousticEntry;
import org.orecruncher.dsurround.config.data.BlockConfig;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;

import java.awt.*;
import java.util.Random;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class BlockInfo {

    protected final ObjectArray<AcousticEntry> sounds = new ObjectArray<>();

    protected final int version;
    protected String chance = "0.01";

    public BlockInfo(int version) {
        this.version = version;
    }

    public int getVersion() {
        return this.version;
    }

    public void update(BlockConfig config) {
        // Reset of a block clears all registry
        if (config.soundReset != null && config.soundReset)
            this.clearSounds();

        if (config.chance != null)
            this.setChance(config.chance);

        for (final AcousticConfig sr : config.acoustics) {
            if (sr.soundEventId != null) {
                final Identifier res = SoundLibrary.resolveIdentifier(Client.ModId, sr.soundEventId);
                final SoundEvent acoustic = SoundLibrary.getSound(res);
                final int weight = sr.weight;
                final AcousticEntry acousticEntry = new AcousticEntry(acoustic, sr.conditions, weight);
                this.addSound(acousticEntry);
            }
        }
    }

    public void setChance(final String chance) {
        this.chance = chance;
    }

    public String getChance() {
        return this.chance;
    }

    public void addSound(final AcousticEntry sound) {
        this.sounds.add(sound);
    }

    public void clearSounds() {
        this.sounds.clear();
    }

    public boolean hasSounds() {
        return this.sounds.size() > 0;
    }

    public SoundEvent getSoundToPlay(final Random random) {
        if (this.sounds.size() > 0) {
            var chance = ConditionEvaluator.INSTANCE.eval(this.chance);
            if (chance instanceof Double c && random.nextDouble() < c) {
                var candidates = this.sounds.stream().filter(AcousticEntry::matches).collect(Collectors.toList());
                return new WeightTable<>(candidates).next();
            }
        }
        return null;
    }

    public void trim() {
        this.sounds.trim();
    }

    public String toString() {
        if (this.sounds.size() == 0)
            return "NO SOUNDS";

        final StringBuilder builder = new StringBuilder();
        builder.append("selection chance: ").append(this.getChance());
        builder.append("; sounds [\n");
        builder.append(this.sounds.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
        builder.append("\n]");

        return builder.toString();
    }
}