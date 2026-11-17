// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The individual sound policies the mod ships. They seed soundconfig.json on the first run, and the sound
 * configuration screen's Ctrl+Reset puts a sound back to them. A sound not listed here defaults to full volume with
 * no action.
 * <p>
 * Seeding happens only when there is no soundconfig.json, so a policy added here later doesn't reach players who
 * already have one, except through Ctrl+Reset.
 */
public final class SoundConfigDefaults {

    /**
     * What a sound gets out of the box.
     */
    public record Policy(int volumeScale, SoundAction action) {
        public static final Policy NONE = new Policy(100, SoundAction.DEFAULT);
    }

    private static final Map<Identifier, Policy> POLICIES = new LinkedHashMap<>();

    static {
        add("minecraft:entity.sheep.ambient", 100, SoundAction.CULL);
        add("minecraft:entity.chicken.ambient", 100, SoundAction.CULL);
        add("minecraft:entity.cow.ambient", 100, SoundAction.CULL);
        add("minecraft:entity.pig.ambient", 100, SoundAction.CULL);
        add("minecraft:entity.llama.ambient", 100, SoundAction.CULL);
        add("minecraft:entity.wither.spawn", 10, SoundAction.CULL);
        add("minecraft:entity.wither.death", 10, SoundAction.CULL);
        add("minecraft:entity.ender_dragon.death", 10, SoundAction.CULL);

        // Since we have waterfall sounds
        add("minecraft:block.water.ambient", 100, SoundAction.BLOCK);
    }

    private SoundConfigDefaults() {
    }

    private static void add(String id, int volumeScale, SoundAction action) {
        POLICIES.put(Identifier.parse(id), new Policy(volumeScale, action));
    }

    /**
     * The policy the mod ships for a sound, or {@link Policy#NONE}.
     */
    public static Policy forSound(Identifier sound) {
        return POLICIES.getOrDefault(sound, Policy.NONE);
    }

    /**
     * A fresh entry for each shipped policy, for seeding soundconfig.json.
     */
    public static List<IndividualSoundConfigEntry> entries() {
        return POLICIES.entrySet().stream()
                .map(e -> new IndividualSoundConfigEntry(e.getKey(), e.getValue().volumeScale(), e.getValue().action()))
                .toList();
    }
}
