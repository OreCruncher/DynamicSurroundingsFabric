package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public interface IItemLibrary extends ILibrary {

    ISoundFactory getItemEquipSound(ItemStack stack);

    Optional<ISoundFactory> getItemSwingSound(ItemStack stack);

    Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack);
}
