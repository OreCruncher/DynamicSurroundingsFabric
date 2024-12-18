package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;

public interface IItemLibrary extends ILibrary {

    Optional<ISoundFactory> getItemEquipSound(ItemStack stack);

    Optional<ISoundFactory> getItemSwingSound(ItemStack stack);

    Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack);
}
