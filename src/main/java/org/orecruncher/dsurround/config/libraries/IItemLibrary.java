package org.orecruncher.dsurround.config.libraries;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.sound.ISoundFactory;

public interface IItemLibrary extends ILibrary {

    ISoundFactory getItemEquipSound(ItemStack stack);

    @Nullable ISoundFactory getItemSwingSound(ItemStack stack);
}
