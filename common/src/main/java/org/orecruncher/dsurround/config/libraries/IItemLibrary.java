// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * The sounds items make when equipped, swung or worn while walking. Client thread only.
 */
public interface IItemLibrary extends ILibrary {

    Optional<ISoundFactory> getItemEquipSound(ItemStack stack);

    Optional<ISoundFactory> getItemSwingSound(ItemStack stack);

    Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack);

    /**
     * The item dump for one mod's items (by namespace), or every item for null.
     */
    Stream<String> dump(@Nullable String namespace);
}
